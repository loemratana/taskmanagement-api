package com.ratana.task_management_api.announcement.service;

import com.ratana.task_management_api.announcement.dto.request.AnnouncementFilterRequest;
import com.ratana.task_management_api.announcement.dto.request.AnnouncementRequest;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementAnalyticsResponse;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementAnalyticsResponse.DepartmentEngagement;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementAnalyticsResponse.MonthlyCount;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementAnalyticsResponse.MonthlyRate;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementAnalyticsResponse.MostViewed;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementAnalyticsResponse.RecentlyViewed;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementAnalyticsResponse.TypeCount;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementFeedResponse;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementResponse;
import com.ratana.task_management_api.announcement.dto.response.AnnouncementStatsResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.announcement.enums.AnnouncementStatus;
import com.ratana.task_management_api.announcement.enums.PublishMode;
import com.ratana.task_management_api.announcement.event.AnnouncementPublishedEvent;
import com.ratana.task_management_api.common.exception.InvalidRequestException;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.announcement.mapper.AnnouncementMapper;
import com.ratana.task_management_api.announcement.entity.Announcement;
import com.ratana.task_management_api.announcement.entity.AnnouncementRead;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.announcement.repository.AnnouncementReadRepository;
import com.ratana.task_management_api.announcement.repository.AnnouncementRepository;
import com.ratana.task_management_api.organization.repository.DepartmentRepository;
import com.ratana.task_management_api.user.repository.UserRepository;
import com.ratana.task_management_api.announcement.repository.projection.AnnouncementMetrics;
import com.ratana.task_management_api.announcement.specification.AnnouncementSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementServiceImpl implements AnnouncementService {

    /** Month buckets in analytics follow office time. */
    private static final ZoneId OFFICE_ZONE = ZoneId.of("Asia/Phnom_Penh");
    private static final int ANALYTICS_MONTHS = 6;
    /** Statuses whose announcements were actually sent (archived ones may have been). */
    private static final List<AnnouncementStatus> SENT_STATUSES =
            List.of(AnnouncementStatus.PUBLISHED, AnnouncementStatus.EXPIRED, AnnouncementStatus.ARCHIVED);
    /** Rich-text editor output: formatting, lists, links, images, tables. No scripts, styles or handlers. */
    private static final Safelist DESCRIPTION_SAFELIST = Safelist.relaxed();

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReadRepository readRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AnnouncementMapper announcementMapper;
    private final AnnouncementRecipientResolver recipientResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    // ---------------------------------------------------------------- Admin

    @Override
    @Transactional
    public AnnouncementResponse create(AnnouncementRequest request, UUID actorId) {
        log.debug("Creating announcement '{}' by {}", request.getTitle(), actorId);
        Instant now = clock.instant();

        Announcement announcement = announcementMapper.toEntity(request);
        announcement.setDescription(sanitize(request.getDescription()));
        announcement.setCreatedBy(userRepository.getReferenceById(actorId));
        applyAudience(announcement, request);

        boolean publishNow = applyInitialStatus(announcement, request, now);
        Announcement saved = announcementRepository.save(announcement);
        if (publishNow) {
            publishNow(saved, actorId, now);
        }
        log.info("Announcement created: {} ({}) status={}", saved.getTitle(), saved.getId(), saved.getStatus());
        return announcementMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AnnouncementResponse update(UUID id, AnnouncementRequest request, UUID actorId) {
        Announcement announcement = getWithDetailsOrThrow(id);
        if (announcement.getStatus() == AnnouncementStatus.ARCHIVED) {
            log.warn("Update rejected: announcement {} is archived", id);
            throw new InvalidRequestException("Archived announcements cannot be edited");
        }
        Instant now = clock.instant();

        if (announcement.isUnpublished()) {
            announcementMapper.updateEntity(announcement, request);
            announcement.setDescription(sanitize(request.getDescription()));
            applyAudience(announcement, request);
            boolean publishNow = applyInitialStatus(announcement, request, now);
            if (publishNow) {
                publishNow(announcement, actorId, now);
            }
        } else {
            // Already sent: content, end date, pin and audience may change; timing stays and nobody is re-notified.
            Instant publishAt = announcement.getPublishAt();
            PublishMode publishMode = announcement.getPublishMode();
            announcementMapper.updateEntity(announcement, request);
            announcement.setPublishAt(publishAt);
            announcement.setPublishMode(publishMode);
            announcement.setDescription(sanitize(request.getDescription()));
            applyAudience(announcement, request);
            announcement.setRecipientCount(recipientResolver.resolve(announcement).size());
            refreshExpiry(announcement, now);
        }

        Announcement saved = announcementRepository.save(announcement);
        log.info("Announcement updated: {} status={} by {}", id, saved.getStatus(), actorId);
        return toResponseWithMetrics(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AnnouncementResponse getById(UUID id) {
        log.debug("Fetching announcement {}", id);
        return toResponseWithMetrics(getWithDetailsOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> search(AnnouncementFilterRequest filter, Pageable pageable) {
        log.debug("Searching announcements. filter={}, page={}, size={}",
                filter, pageable.getPageNumber(), pageable.getPageSize());
        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Announcement> page = announcementRepository.findAll(
                AnnouncementSpecifications.fromFilter(filter), sorted);
        Map<UUID, AnnouncementMetrics> metrics = metricsFor(page.getContent());
        return PageResponse.from(page.map(a -> announcementMapper.toResponse(a, metrics.get(a.getId()))));
    }

    @Override
    @Transactional
    public AnnouncementResponse publish(UUID id, UUID actorId) {
        Announcement announcement = getWithDetailsOrThrow(id);
        if (!announcement.isUnpublished()) {
            log.warn("Publish rejected: announcement {} is {}", id, announcement.getStatus());
            throw new InvalidRequestException("Only draft or scheduled announcements can be published");
        }
        Instant now = clock.instant();
        announcement.setPublishMode(PublishMode.NOW);
        announcement.setPublishAt(now);
        publishNow(announcement, actorId, now);
        log.info("Announcement {} published manually by {}", id, actorId);
        return toResponseWithMetrics(announcement);
    }

    @Override
    @Transactional
    public int publishAll(Set<UUID> ids, UUID actorId) {
        Instant now = clock.instant();
        int published = 0;
        for (Announcement announcement : announcementRepository.findWithDetailsByIdIn(ids)) {
            if (!announcement.isUnpublished() || isEnded(announcement, now)) {
                log.debug("Bulk publish skipped {} (status={})", announcement.getId(), announcement.getStatus());
                continue;
            }
            announcement.setPublishMode(PublishMode.NOW);
            announcement.setPublishAt(now);
            publishNow(announcement, actorId, now);
            published++;
        }
        log.info("Bulk publish by {}: {}/{} announcement(s) published", actorId, published, ids.size());
        return published;
    }

    @Override
    @Transactional
    public AnnouncementResponse archive(UUID id) {
        Announcement announcement = getWithDetailsOrThrow(id);
        if (announcement.getStatus() != AnnouncementStatus.ARCHIVED) {
            announcement.setStatus(AnnouncementStatus.ARCHIVED);
            announcement.setPinned(false);
            log.info("Announcement {} archived", id);
        }
        return toResponseWithMetrics(announcementRepository.save(announcement));
    }

    @Override
    @Transactional
    public AnnouncementResponse duplicate(UUID id, UUID actorId) {
        Announcement source = getWithDetailsOrThrow(id);
        Announcement copy = announcementRepository.save(
                announcementMapper.copyAsDraft(source, userRepository.getReferenceById(actorId)));
        log.info("Announcement {} duplicated as {} by {}", id, copy.getId(), actorId);
        return announcementMapper.toResponse(copy);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (announcementRepository.deleteByIdIn(List.of(id)) == 0) {
            throw new ResourceNotFoundException("Announcement not found: " + id);
        }
        log.info("Announcement {} deleted", id);
    }

    @Override
    @Transactional
    public int deleteAll(Set<UUID> ids) {
        int deleted = announcementRepository.deleteByIdIn(ids);
        log.info("Bulk delete: {}/{} announcement(s) deleted", deleted, ids.size());
        return deleted;
    }

    @Override
    @Transactional(readOnly = true)
    public AnnouncementStatsResponse getStats() {
        long recipients = announcementRepository.sumRecipients(SENT_STATUSES);
        long reads = readRepository.countReadsOfSent();
        return AnnouncementStatsResponse.builder()
                .total(announcementRepository.count())
                .published(announcementRepository.countByStatus(AnnouncementStatus.PUBLISHED))
                .scheduled(announcementRepository.countByStatus(AnnouncementStatus.SCHEDULED))
                .pinned(announcementRepository.countByPinnedTrueAndStatus(AnnouncementStatus.PUBLISHED))
                .upcomingEvents(0)
                .holidaysThisMonth(0)
                .totalViews(readRepository.sumAllViews())
                .readRate(percent(reads, recipients))
                .unreadEmployees(Math.max(0, recipients - reads))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AnnouncementAnalyticsResponse getAnalytics(UUID currentUserId) {
        Instant from = ZonedDateTime.now(clock.withZone(OFFICE_ZONE))
                .minusMonths(ANALYTICS_MONTHS - 1L)
                .withDayOfMonth(1)
                .toLocalDate()
                .atStartOfDay(OFFICE_ZONE)
                .toInstant();

        List<Announcement> recentPublished =
                announcementRepository.findTop5ByStatusOrderByPublishedAtDesc(AnnouncementStatus.PUBLISHED);
        List<Announcement> upcoming =
                announcementRepository.findTop5ByStatusOrderByPublishAtAsc(AnnouncementStatus.SCHEDULED);
        Map<UUID, AnnouncementMetrics> metrics = metricsFor(recentPublished);

        return AnnouncementAnalyticsResponse.builder()
                .monthlyCounts(announcementRepository.countPublishedPerMonth(from).stream()
                        .map(v -> new MonthlyCount(v.getMonth(), v.getCount()))
                        .toList())
                .typeDistribution(announcementRepository.countByType().stream()
                        .map(v -> new TypeCount(v.getType(), v.getCount()))
                        .toList())
                .readRateTrend(readRepository.readRatePerMonth(from).stream()
                        .map(v -> new MonthlyRate(v.getMonth(), v.getCount()))
                        .toList())
                .departmentEngagement(readRepository.departmentEngagement().stream()
                        .map(v -> new DepartmentEngagement(v.getDepartmentId(), v.getDepartment(),
                                percent(v.getReaders(), v.getMembers())))
                        .toList())
                .mostViewed(readRepository.findMostViewed(PageRequest.of(0, 5)).stream()
                        .map(v -> new MostViewed(v.getId(), v.getTitle(), v.getViews()))
                        .toList())
                .recentPublished(recentPublished.stream()
                        .map(a -> announcementMapper.toResponse(a, metrics.get(a.getId())))
                        .toList())
                .upcomingScheduled(upcoming.stream().map(announcementMapper::toResponse).toList())
                .recentlyViewed(readRepository.findTop10ByUserIdOrderByLastViewedAtDesc(currentUserId).stream()
                        .map(r -> new RecentlyViewed(r.getAnnouncement().getId(),
                                r.getAnnouncement().getTitle(), r.getLastViewedAt()))
                        .toList())
                .build();
    }

    // ---------------------------------------------------------------- Employee

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AnnouncementFeedResponse> getFeed(UUID userId, Pageable pageable) {
        User user = getUserOrThrow(userId);
        // The query orders pinned-first itself; a client sort would be appended after it.
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Page<Announcement> page = announcementRepository.findFeed(
                userId, departmentIdOf(user), managerIdOf(user), clock.instant(), unsorted);

        Map<UUID, AnnouncementRead> reads = page.isEmpty()
                ? Map.of()
                : readRepository.findByUserIdAndAnnouncementIdIn(userId, idsOf(page.getContent())).stream()
                        .collect(Collectors.toMap(r -> r.getAnnouncement().getId(), Function.identity()));

        return PageResponse.from(page.map(a -> announcementMapper.toFeedResponse(a, reads.get(a.getId()))));
    }

    @Override
    @Transactional
    public AnnouncementFeedResponse viewFromFeed(UUID id, UUID userId) {
        requireVisible(id, userId);
        readRepository.recordView(id, userId);
        Announcement announcement = getWithDetailsOrThrow(id);
        AnnouncementRead read = readRepository.findByAnnouncementIdAndUserId(id, userId).orElse(null);
        return announcementMapper.toFeedResponse(announcement, read);
    }

    @Override
    @Transactional
    public void markRead(UUID id, UUID userId) {
        requireVisible(id, userId);
        readRepository.markRead(id, userId);
        log.debug("Announcement {} marked read by {}", id, userId);
    }

    // ---------------------------------------------------------------- Scheduler

    @Override
    @Transactional
    public boolean publishIfDue(UUID id) {
        Announcement announcement = announcementRepository.findWithDetailsById(id).orElse(null);
        Instant now = clock.instant();
        if (announcement == null
                || announcement.getStatus() != AnnouncementStatus.SCHEDULED
                || announcement.getPublishAt() == null
                || announcement.getPublishAt().isAfter(now)) {
            return false;
        }
        if (isEnded(announcement, now)) {
            // Window closed before it went out (e.g. downtime): don't notify about something already over.
            announcement.setStatus(AnnouncementStatus.EXPIRED);
            log.warn("Scheduled announcement {} expired before it could be published", id);
            return false;
        }
        publishNow(announcement, null, now);
        log.info("Scheduled announcement {} published", id);
        return true;
    }

    @Override
    @Transactional
    public int expireDue() {
        return announcementRepository.expirePublished(
                clock.instant(), AnnouncementStatus.PUBLISHED, AnnouncementStatus.EXPIRED);
    }

    // ---------------------------------------------------------------- helpers

    /** Sets DRAFT / SCHEDULED, or returns true when the announcement should go out now. */
    private boolean applyInitialStatus(Announcement announcement, AnnouncementRequest request, Instant now) {
        if (request.isDraft()) {
            announcement.setStatus(AnnouncementStatus.DRAFT);
            return false;
        }
        if (request.getPublishMode() == PublishMode.SCHEDULED && request.getPublishAt().isAfter(now)) {
            announcement.setStatus(AnnouncementStatus.SCHEDULED);
            return false;
        }
        if (request.getPublishMode() == PublishMode.NOW) {
            announcement.setPublishAt(now);
        }
        if (isEnded(announcement, now)) {
            throw new InvalidRequestException("End date is already in the past");
        }
        return true;
    }

    /** Marks published, records recipients and queues notifications (sent after commit). */
    private void publishNow(Announcement announcement, UUID actorId, Instant now) {
        announcement.setStatus(AnnouncementStatus.PUBLISHED);
        announcement.setPublishedAt(now);
        announcement.setRecipientCount(recipientResolver.resolve(announcement).size());
        Announcement saved = announcementRepository.save(announcement);
        eventPublisher.publishEvent(new AnnouncementPublishedEvent(saved.getId(), actorId));
    }

    private void refreshExpiry(Announcement announcement, Instant now) {
        boolean ended = isEnded(announcement, now);
        if (announcement.getStatus() == AnnouncementStatus.PUBLISHED && ended) {
            announcement.setStatus(AnnouncementStatus.EXPIRED);
        } else if (announcement.getStatus() == AnnouncementStatus.EXPIRED && !ended) {
            announcement.setStatus(AnnouncementStatus.PUBLISHED);
        }
    }

    private boolean isEnded(Announcement announcement, Instant now) {
        return announcement.getEndAt() != null && !announcement.getEndAt().isAfter(now);
    }

    private void applyAudience(Announcement announcement, AnnouncementRequest request) {
        announcement.getTargetDepartments().clear();
        announcement.getTargetTeamManagers().clear();
        announcement.getTargetUsers().clear();
        switch (request.getAudienceType()) {
            case EVERYONE -> { }
            case DEPARTMENT -> announcement.getTargetDepartments()
                    .addAll(loadAll(departmentRepository, request.getDepartmentIds(), Department::getId, "Department"));
            case TEAM -> announcement.getTargetTeamManagers()
                    .addAll(loadAll(userRepository, request.getTeamIds(), User::getId, "Manager"));
            case SELECTED_EMPLOYEES -> announcement.getTargetUsers()
                    .addAll(loadAll(userRepository, request.getUserIds(), User::getId, "User"));
            // A new audience type must be wired here, or it would silently reach nobody.
            default -> throw new IllegalStateException("Unhandled audience type: " + request.getAudienceType());
        }
    }

    private <T> Set<T> loadAll(JpaRepository<T, UUID> repository, Collection<UUID> ids,
                               Function<T, UUID> idOf, String label) {
        List<T> found = repository.findAllById(ids);
        if (found.size() != ids.size()) {
            Set<UUID> missing = new HashSet<>(ids);
            found.stream().map(idOf).forEach(missing::remove);
            throw new ResourceNotFoundException(label + "(s) not found: " + missing);
        }
        return new HashSet<>(found);
    }

    private void requireVisible(UUID id, UUID userId) {
        User user = getUserOrThrow(userId);
        if (!announcementRepository.isVisibleTo(id, userId, departmentIdOf(user), managerIdOf(user), clock.instant())) {
            // 404 rather than 403: don't reveal announcements addressed to others.
            throw new ResourceNotFoundException("Announcement not found: " + id);
        }
    }

    private String sanitize(String html) {
        return StringUtils.hasText(html) ? Jsoup.clean(html, DESCRIPTION_SAFELIST) : null;
    }

    private AnnouncementResponse toResponseWithMetrics(Announcement announcement) {
        return announcementMapper.toResponse(announcement, metricsFor(List.of(announcement)).get(announcement.getId()));
    }

    private Map<UUID, AnnouncementMetrics> metricsFor(List<Announcement> announcements) {
        if (announcements.isEmpty()) {
            return Map.of();
        }
        return readRepository.findMetrics(idsOf(announcements)).stream()
                .collect(Collectors.toMap(AnnouncementMetrics::getAnnouncementId, Function.identity()));
    }

    private List<UUID> idsOf(List<Announcement> announcements) {
        return announcements.stream().map(Announcement::getId).toList();
    }

    private double percent(long part, long whole) {
        return whole == 0 ? 0 : Math.round(1000.0 * part / whole) / 10.0;
    }

    private Announcement getWithDetailsOrThrow(UUID id) {
        return announcementRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement not found: " + id));
    }

    private User getUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private UUID departmentIdOf(User user) {
        return user.getDepartment() == null ? null : user.getDepartment().getId();
    }

    private UUID managerIdOf(User user) {
        return user.getManager() == null ? null : user.getManager().getId();
    }
}
