package com.ratana.task_management_api.performance.service;

import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.organization.repository.DepartmentRepository;
import com.ratana.task_management_api.performance.config.PerformanceProperties;
import com.ratana.task_management_api.performance.dto.PerformanceLeaderboardResponse;
import com.ratana.task_management_api.performance.dto.PerformanceLeaderboardResponse.Entry;
import com.ratana.task_management_api.performance.enums.PerformancePeriod;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.entity.TaskAssignment;
import com.ratana.task_management_api.task.entity.TaskSubmission;
import com.ratana.task_management_api.task.enums.Priority;
import com.ratana.task_management_api.task.enums.SubmissionStatus;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.task.repository.TaskSubmissionRepository;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PerformanceLeaderboardServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Phnom_Penh");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 30);

    private final DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final TaskSubmissionRepository submissionRepository = mock(TaskSubmissionRepository.class);
    private PerformanceLeaderboardService service;

    private final UUID departmentId = UUID.randomUUID();
    private final UserPrincipal actor = mock(UserPrincipal.class);

    @BeforeEach
    void setUp() {
        PerformanceProperties properties = PerformanceScoreCalculatorTest.defaults(0);
        Clock clock = Clock.fixed(TODAY.atTime(12, 0).atZone(ZONE).toInstant(), ZONE);
        service = new PerformanceLeaderboardService(departmentRepository, userRepository, taskRepository,
                submissionRepository, new PerformanceScoreCalculator(properties), properties, clock);

        Department department = new Department();
        department.setId(departmentId);
        department.setName("IT");
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));
        when(actor.getDepartmentId()).thenReturn(departmentId);
    }

    private static User user(String name) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName(name);
        return user;
    }

    private static Instant at(int day) {
        return LocalDate.of(2026, 11, day).atTime(10, 0).atZone(ZONE).toInstant();
    }

    private static Task doneTask(User assignee, int submittedDay, List<TaskSubmission> sink) {
        Task task = Task.builder().id(UUID.randomUUID()).status(TaskStatus.DONE).priority(Priority.MEDIUM)
                .dueDate(LocalDate.of(2026, 11, 20)).assignee(assignee).assignments(new ArrayList<>())
                .createdAt(at(1)).completedAt(at(submittedDay + 1)).build();
        sink.add(TaskSubmission.builder().task(task).status(SubmissionStatus.APPROVED).round(1)
                .submittedAt(at(submittedDay)).build());
        return task;
    }

    @Test
    @DisplayName("Ranks rated members by score, lists the unrated after, and credits collaborators")
    void ranking() {
        User onTime = user("On Time");
        User late = user("Always Late");
        User newcomer = user("Newcomer");
        when(userRepository.findByDepartmentId(departmentId)).thenReturn(List.of(late, newcomer, onTime));

        List<Task> tasks = new ArrayList<>();
        List<TaskSubmission> submissions = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(doneTask(onTime, 15, submissions));
            tasks.add(doneTask(late, 25, submissions));
        }
        // Shared task: newcomer is a collaborator, so it is their only counted task.
        Task shared = doneTask(onTime, 15, submissions);
        shared.getAssignments().add(TaskAssignment.builder().task(shared).user(newcomer).build());
        tasks.add(shared);

        when(taskRepository.findActiveForPerformance(any())).thenReturn(tasks);
        when(submissionRepository.findByTaskIds(any())).thenReturn(submissions);

        PerformanceLeaderboardResponse response =
                service.leaderboard(null, PerformancePeriod.MONTHLY, null, null, null, actor);

        assertThat(response.from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(response.to()).isEqualTo(LocalDate.of(2026, 11, 30));
        List<Entry> entries = response.entries();
        assertThat(entries).extracting(Entry::fullName).containsExactly("On Time", "Always Late", "Newcomer");
        assertThat(entries).extracting(Entry::rank).containsExactly(1, 2, null);
        assertThat(entries.get(0).counts().tasks()).isEqualTo(5);
        assertThat(entries.get(0).stars()).isNotNull();
        assertThat(entries.get(1).counts().late()).isEqualTo(4);
        assertThat(entries.get(2).rated()).isFalse();
        assertThat(entries.get(2).stars()).isNull();
        assertThat(entries.get(2).counts().tasks()).isEqualTo(1);
    }

    @Test
    @DisplayName("Quarter 4 runs October 1 to December 31; invalid numbers are rejected")
    void resolvePeriod_quarter() {
        PerformanceLeaderboardService.Period q4 =
                PerformanceLeaderboardService.resolvePeriod(PerformancePeriod.QUARTERLY, 2026, null, TODAY);
        assertThat(q4.from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(q4.to()).isEqualTo(LocalDate.of(2026, 12, 31));

        assertThatThrownBy(() -> PerformanceLeaderboardService.resolvePeriod(PerformancePeriod.QUARTERLY, 2026, 5, TODAY))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PerformanceLeaderboardService.resolvePeriod(PerformancePeriod.MONTHLY, 2026, 13, TODAY))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("A caller without a department must name one")
    void noDepartment_badRequest() {
        when(actor.getDepartmentId()).thenReturn(null);
        assertThatThrownBy(() -> service.leaderboard(null, PerformancePeriod.MONTHLY, null, null, null, actor))
                .isInstanceOf(BadRequestException.class);
    }
}
