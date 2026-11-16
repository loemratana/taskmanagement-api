package com.ratana.task_management_api.performance.service;

import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.organization.repository.DepartmentRepository;
import com.ratana.task_management_api.performance.dto.PerformanceAwardResponse;
import com.ratana.task_management_api.performance.dto.PerformanceLeaderboardResponse;
import com.ratana.task_management_api.performance.dto.PerformanceLeaderboardResponse.Entry;
import com.ratana.task_management_api.performance.entity.PerformanceAward;
import com.ratana.task_management_api.performance.entity.PerformanceAwardEntry;
import com.ratana.task_management_api.performance.entity.PerformanceAwardView;
import com.ratana.task_management_api.performance.enums.PerformancePeriod;
import com.ratana.task_management_api.performance.event.PerformanceAwardPublishedEvent;
import com.ratana.task_management_api.performance.repository.PerformanceAwardRepository;
import com.ratana.task_management_api.performance.repository.PerformanceAwardViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PerformanceAwardServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");

    private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
    private final PerformanceAwardRepository awardRepository = mock(PerformanceAwardRepository.class);
    private final PerformanceAwardViewRepository viewRepository = mock(PerformanceAwardViewRepository.class);
    private final PerformanceLeaderboardService leaderboardService = mock(PerformanceLeaderboardService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private PerformanceAwardService service;

    private final Department department = new Department();

    /** 1 December 2026, 09:00 in Phnom Penh: the monthly run for November. */
    private PerformanceAwardService serviceAt(LocalDate date) {
        Clock clock = Clock.fixed(date.atTime(9, 0).atZone(ZONE).toInstant(), ZONE);
        return new PerformanceAwardService(departmentRepository, awardRepository, viewRepository, leaderboardService,
                PerformanceScoreCalculatorTest.defaults(0), eventPublisher, mock(PlatformTransactionManager.class), clock);
    }

    @BeforeEach
    void setUp() {
        service = serviceAt(LocalDate.of(2026, 12, 1));
        department.setId(UUID.randomUUID());
        department.setName("IT");
        department.setActive(true);
        when(departmentRepository.findAll()).thenReturn(List.of(department));
        when(departmentRepository.findById(department.getId())).thenReturn(Optional.of(department));
        when(awardRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            PerformanceAward award = invocation.getArgument(0);
            award.setId(UUID.randomUUID());
            return award;
        });
    }

    private static Entry rated(int rank, String name, double stars) {
        return Entry.builder().rank(rank).userId(UUID.randomUUID()).fullName(name).rated(true)
                .score(90.0 - rank).stars(stars).rawScore(90.0)
                .counts(PerformanceLeaderboardResponse.Counts.builder().tasks(5).build()).build();
    }

    private void boardReturns(PerformancePeriod period, Integer number, List<Entry> entries) {
        PerformanceLeaderboardResponse board = PerformanceLeaderboardResponse.builder()
                .departmentId(department.getId()).departmentName("IT").period(period)
                .from(LocalDate.of(2026, 11, 1)).to(LocalDate.of(2026, 11, 30)).entries(entries).build();
        when(leaderboardService.leaderboard(eq(department.getId()), eq(period), anyInt(), eq(number), any(), any()))
                .thenReturn(board);
    }

    @Test
    @DisplayName("1 December: freezes November's ranking and announces the top 3")
    void publishPreviousMonth_savesAndAnnounces() {
        Entry unrated = Entry.builder().userId(UUID.randomUUID()).fullName("New").rated(false)
                .counts(PerformanceLeaderboardResponse.Counts.builder().tasks(1).build()).build();
        boardReturns(PerformancePeriod.MONTHLY, 11, List.of(
                rated(1, "Dara", 5), rated(2, "Sok", 4.5), rated(3, "Vy", 4), rated(4, "Lina", 3.5), unrated));

        assertThat(service.publishPreviousMonth()).isEqualTo(1);

        ArgumentCaptor<PerformanceAward> saved = ArgumentCaptor.forClass(PerformanceAward.class);
        verify(awardRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPeriodYear()).isEqualTo(2026);
        assertThat(saved.getValue().getPeriodNumber()).isEqualTo(11);
        assertThat(saved.getValue().getEntries()).extracting(PerformanceAwardEntry::getFullName)
                .containsExactly("Dara", "Sok", "Vy", "Lina");   // the unrated aren't frozen

        ArgumentCaptor<PerformanceAwardPublishedEvent> event = ArgumentCaptor.forClass(PerformanceAwardPublishedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().title()).isEqualTo("🏆 Top performers · November 2026");
        assertThat(event.getValue().message()).isEqualTo("IT: 1. Dara ★5 · 2. Sok ★4.5 · 3. Vy ★4");
    }

    @Test
    @DisplayName("2 January: announces last year, stored with period number 0")
    void publishPreviousYear() {
        service = serviceAt(LocalDate.of(2027, 1, 2));
        boardReturns(PerformancePeriod.YEARLY, null, List.of(rated(1, "Dara", 5)));

        assertThat(service.publishPreviousYear()).isEqualTo(1);

        ArgumentCaptor<PerformanceAward> saved = ArgumentCaptor.forClass(PerformanceAward.class);
        verify(awardRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPeriodYear()).isEqualTo(2026);
        assertThat(saved.getValue().getPeriodNumber()).isEqualTo(PerformanceAward.YEARLY_NUMBER);
    }

    @Test
    @DisplayName("A second run skips departments already announced; nobody rated means no award")
    void publish_isIdempotent_andSkipsEmpty() {
        when(awardRepository.existsByDepartmentIdAndPeriodAndPeriodYearAndPeriodNumber(
                department.getId(), PerformancePeriod.MONTHLY, 2026, 11)).thenReturn(true);
        assertThat(service.publish(PerformancePeriod.MONTHLY, 2026, 11)).isZero();

        boardReturns(PerformancePeriod.MONTHLY, 10, List.of());
        assertThat(service.publish(PerformancePeriod.MONTHLY, 2026, 10)).isZero();

        verify(awardRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("A period that hasn't ended can't be announced")
    void publish_unfinishedPeriod_refused() {
        assertThatThrownBy(() -> service.publish(PerformancePeriod.MONTHLY, 2026, 12))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.publish(PerformancePeriod.YEARLY, 2026, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Pending popups carry the top 3 and the viewer's own place")
    void pending_topThreeAndMyEntry() {
        UUID me = UUID.randomUUID();
        PerformanceAward award = PerformanceAward.builder().id(UUID.randomUUID()).departmentId(department.getId())
                .departmentName("IT").period(PerformancePeriod.MONTHLY).periodYear(2026).periodNumber(11)
                .periodFrom(LocalDate.of(2026, 11, 1)).periodTo(LocalDate.of(2026, 11, 30)).build();
        for (int rank = 1; rank <= 5; rank++) {
            award.addEntry(PerformanceAwardEntry.builder().rank(rank).fullName("P" + rank)
                    .userId(rank == 5 ? me : UUID.randomUUID()).score(80.0).stars(4.0).tasks(4).build());
        }
        UserPrincipal actor = mock(UserPrincipal.class);
        when(actor.getId()).thenReturn(me);
        when(actor.getDepartmentId()).thenReturn(department.getId());
        when(awardRepository.findPending(eq(department.getId()), eq(me), any(), any())).thenReturn(List.of(award));

        List<PerformanceAwardResponse> pending = service.pending(actor);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).label()).isEqualTo("November 2026");
        assertThat(pending.get(0).entries()).hasSize(3);
        assertThat(pending.get(0).ratedCount()).isEqualTo(5);
        assertThat(pending.get(0).myEntry().rank()).isEqualTo(5);
    }

    @Test
    @DisplayName("Closing a popup twice records one view")
    void markSeen_idempotent() {
        UUID awardId = UUID.randomUUID();
        UserPrincipal actor = mock(UserPrincipal.class);
        UUID me = UUID.randomUUID();
        when(actor.getId()).thenReturn(me);
        when(awardRepository.existsById(awardId)).thenReturn(true);
        when(viewRepository.existsByAwardIdAndUserId(awardId, me)).thenReturn(false, true);

        service.markSeen(awardId, actor);
        service.markSeen(awardId, actor);

        verify(viewRepository).save(any(PerformanceAwardView.class));
    }

    @Test
    @DisplayName("Yearly period covers 1 January to 31 December")
    void resolvePeriod_yearly() {
        PerformanceLeaderboardService.Period year = PerformanceLeaderboardService.resolvePeriod(
                PerformancePeriod.YEARLY, 2026, null, LocalDate.of(2027, 1, 2));
        assertThat(year.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(year.to()).isEqualTo(LocalDate.of(2026, 12, 31));
    }
}
