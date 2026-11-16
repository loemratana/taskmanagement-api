package com.ratana.task_management_api.performance.service;

import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.organization.repository.DepartmentRepository;
import com.ratana.task_management_api.performance.config.PerformanceProperties;
import com.ratana.task_management_api.performance.dto.PerformanceAwardResponse;
import com.ratana.task_management_api.performance.dto.PerformanceLeaderboardResponse;
import com.ratana.task_management_api.performance.entity.PerformanceAward;
import com.ratana.task_management_api.performance.entity.PerformanceAwardEntry;
import com.ratana.task_management_api.performance.entity.PerformanceAwardView;
import com.ratana.task_management_api.performance.enums.PerformancePeriod;
import com.ratana.task_management_api.performance.event.PerformanceAwardPublishedEvent;
import com.ratana.task_management_api.performance.repository.PerformanceAwardRepository;
import com.ratana.task_management_api.performance.repository.PerformanceAwardViewRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Monthly and yearly top-performer announcements. {@link #publish} freezes each department's
 * ranking into a {@link PerformanceAward} and notifies its members; the app shows each award as
 * a popup until the user closes it.
 */
@Service
@Slf4j
public class PerformanceAwardService {

    /** People shown in the popup and the push message. */
    static final int POPUP_TOP = 3;
    /** Older awards are no longer shown as popups, even if never closed. */
    static final Duration PENDING_WINDOW = Duration.ofDays(45);
    static final int MAX_PENDING = 3;

    private final DepartmentRepository departmentRepository;
    private final PerformanceAwardRepository awardRepository;
    private final PerformanceAwardViewRepository viewRepository;
    private final PerformanceLeaderboardService leaderboardService;
    private final PerformanceProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PerformanceAwardService(DepartmentRepository departmentRepository,
                                   PerformanceAwardRepository awardRepository,
                                   PerformanceAwardViewRepository viewRepository,
                                   PerformanceLeaderboardService leaderboardService,
                                   PerformanceProperties properties,
                                   ApplicationEventPublisher eventPublisher,
                                   PlatformTransactionManager transactionManager,
                                   Clock clock) {
        this.departmentRepository = departmentRepository;
        this.awardRepository = awardRepository;
        this.viewRepository = viewRepository;
        this.leaderboardService = leaderboardService;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /** Scheduler, 1st of the month 09:00: last month's top performers. */
    public int publishPreviousMonth() {
        YearMonth previous = YearMonth.from(today()).minusMonths(1);
        return publish(PerformancePeriod.MONTHLY, previous.getYear(), previous.getMonthValue());
    }

    /** Scheduler, 2 January 09:00: last year's top performers. */
    public int publishPreviousYear() {
        return publish(PerformancePeriod.YEARLY, today().getYear() - 1, null);
    }

    /**
     * Publishes one award per active department for a finished period. Departments that already have
     * this award, or where nobody was rated, are skipped, so running it twice is harmless.
     *
     * @return how many awards were published
     */
    public int publish(PerformancePeriod period, int year, Integer number) {
        LocalDate today = today();
        PerformanceLeaderboardService.Period range =
                PerformanceLeaderboardService.resolvePeriod(period, year, number, today);
        if (!range.to().isBefore(today)) {
            throw new BadRequestException("Only a finished period can be announced; %s ends on %s."
                    .formatted(label(period, year, number), range.to()));
        }

        int published = 0;
        List<Department> departments = departmentRepository.findAll().stream().filter(Department::isActive).toList();
        for (Department department : departments) {
            try {
                Boolean done = transactionTemplate.execute(status -> publishOne(department.getId(), period, year, number));
                if (Boolean.TRUE.equals(done)) {
                    published++;
                }
            } catch (DataIntegrityViolationException e) {
                // Another run (or server) saved it first; the unique key kept it to one award.
                log.info("Award {} {} for department {} already published elsewhere", period, year, department.getId());
            } catch (RuntimeException e) {
                log.error("Publishing award {} {}/{} for department {} failed", period, year, number, department.getId(), e);
            }
        }
        log.info("Published {} {} award(s) for {} across {} department(s)",
                published, period, label(period, year, number), departments.size());
        return published;
    }

    private boolean publishOne(UUID departmentId, PerformancePeriod period, int year, Integer number) {
        int storedNumber = period == PerformancePeriod.YEARLY ? PerformanceAward.YEARLY_NUMBER : number;
        if (awardRepository.existsByDepartmentIdAndPeriodAndPeriodYearAndPeriodNumber(departmentId, period, year, storedNumber)) {
            return false;
        }
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + departmentId));
        PerformanceLeaderboardResponse board =
                leaderboardService.leaderboard(departmentId, period, year, number, null, null);
        List<PerformanceLeaderboardResponse.Entry> rated = board.entries().stream()
                .filter(PerformanceLeaderboardResponse.Entry::rated)
                .toList();
        if (rated.isEmpty()) {
            log.info("No award for department {} {} {}: nobody was rated", departmentId, period, label(period, year, number));
            return false;
        }

        PerformanceAward award = PerformanceAward.builder()
                .departmentId(departmentId)
                .departmentName(department.getName())
                .period(period)
                .periodYear(year)
                .periodNumber(storedNumber)
                .periodFrom(board.from())
                .periodTo(board.to())
                .publishedAt(clock.instant())
                .build();
        for (PerformanceLeaderboardResponse.Entry entry : rated) {
            award.addEntry(PerformanceAwardEntry.builder()
                    .rank(entry.rank())
                    .userId(entry.userId())
                    .fullName(entry.fullName())
                    .avatarUrl(entry.avatarUrl())
                    .positionName(entry.positionName())
                    .score(entry.score())
                    .stars(entry.stars())
                    .tasks(entry.counts().tasks())
                    .build());
        }
        PerformanceAward saved = awardRepository.saveAndFlush(award);

        String label = label(period, year, number);
        String podium = rated.stream()
                .limit(POPUP_TOP)
                .map(entry -> "%d. %s ★%s".formatted(entry.rank(), entry.fullName(), formatStars(entry.stars())))
                .collect(Collectors.joining(" · "));
        UUID managerId = department.getManager() != null ? department.getManager().getId() : null;
        eventPublisher.publishEvent(new PerformanceAwardPublishedEvent(saved.getId(), departmentId, managerId,
                "🏆 Top performers · " + label,
                department.getName() + ": " + podium));
        log.info("Award {} published for department {} {} with {} rated", saved.getId(), departmentId, label, rated.size());
        return true;
    }

    /** Awards for the caller's department they haven't closed yet, newest first, each with the top 3. */
    @Transactional(readOnly = true)
    public List<PerformanceAwardResponse> pending(UserPrincipal actor) {
        if (actor.getDepartmentId() == null) {
            return List.of();
        }
        return awardRepository.findPending(actor.getDepartmentId(), actor.getId(),
                        clock.instant().minus(PENDING_WINDOW), PageRequest.of(0, MAX_PENDING)).stream()
                .map(award -> toResponse(award, actor.getId(), POPUP_TOP))
                .toList();
    }

    /** The full frozen ranking ("See all"). Open to every signed-in user, like the leaderboard. */
    @Transactional(readOnly = true)
    public PerformanceAwardResponse get(UUID awardId, UserPrincipal actor) {
        PerformanceAward award = awardRepository.findWithEntriesById(awardId)
                .orElseThrow(() -> new ResourceNotFoundException("Award not found: " + awardId));
        return toResponse(award, actor.getId(), Integer.MAX_VALUE);
    }

    /** The caller closed the popup. Idempotent. */
    @Transactional
    public void markSeen(UUID awardId, UserPrincipal actor) {
        if (!awardRepository.existsById(awardId)) {
            throw new ResourceNotFoundException("Award not found: " + awardId);
        }
        if (!viewRepository.existsByAwardIdAndUserId(awardId, actor.getId())) {
            viewRepository.save(PerformanceAwardView.builder()
                    .awardId(awardId)
                    .userId(actor.getId())
                    .seenAt(clock.instant())
                    .build());
        }
    }

    private PerformanceAwardResponse toResponse(PerformanceAward award, UUID viewerId, int limit) {
        List<PerformanceAwardResponse.Entry> all = award.getEntries().stream().map(PerformanceAwardService::toEntry).toList();
        Integer number = award.getPeriod() == PerformancePeriod.YEARLY ? null : award.getPeriodNumber();
        return PerformanceAwardResponse.builder()
                .id(award.getId())
                .departmentId(award.getDepartmentId())
                .departmentName(award.getDepartmentName())
                .period(award.getPeriod())
                .year(award.getPeriodYear())
                .number(number)
                .label(label(award.getPeriod(), award.getPeriodYear(), number))
                .from(award.getPeriodFrom())
                .to(award.getPeriodTo())
                .publishedAt(award.getPublishedAt())
                .ratedCount(all.size())
                .entries(all.stream().limit(limit).toList())
                .myEntry(all.stream().filter(entry -> entry.userId().equals(viewerId)).findFirst().orElse(null))
                .build();
    }

    private static PerformanceAwardResponse.Entry toEntry(PerformanceAwardEntry entry) {
        return PerformanceAwardResponse.Entry.builder()
                .rank(entry.getRank())
                .userId(entry.getUserId())
                .fullName(entry.getFullName())
                .avatarUrl(entry.getAvatarUrl())
                .positionName(entry.getPositionName())
                .score(entry.getScore())
                .stars(entry.getStars())
                .tasks(entry.getTasks())
                .build();
    }

    /** "November 2026", "Q4 2026" or "2026". */
    static String label(PerformancePeriod period, int year, Integer number) {
        return switch (period) {
            case YEARLY -> String.valueOf(year);
            case QUARTERLY -> "Q%d %d".formatted(number, year);
            case MONTHLY -> Month.of(number).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
        };
    }

    /** 5 → "5", 4.5 → "4.5". */
    private static String formatStars(double stars) {
        return stars == Math.rint(stars) ? String.valueOf((int) stars) : String.valueOf(stars);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(properties.zone()));
    }
}
