package com.ratana.task_management_api.performance.service;

import com.ratana.task_management_api.common.exception.BadRequestException;
import com.ratana.task_management_api.common.exception.ResourceNotFoundException;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.organization.repository.DepartmentRepository;
import com.ratana.task_management_api.performance.config.PerformanceProperties;
import com.ratana.task_management_api.performance.dto.PerformanceLeaderboardResponse;
import com.ratana.task_management_api.performance.dto.PerformanceLeaderboardResponse.Entry;
import com.ratana.task_management_api.performance.enums.PerformancePeriod;
import com.ratana.task_management_api.performance.service.PerformanceScoreCalculator.MemberScore;
import com.ratana.task_management_api.performance.service.PerformanceScoreCalculator.TaskFacts;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.entity.TaskAssignment;
import com.ratana.task_management_api.task.entity.TaskSubmission;
import com.ratana.task_management_api.task.enums.SubmissionStatus;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.task.repository.TaskSubmissionRepository;
import com.ratana.task_management_api.user.entity.User;
import com.ratana.task_management_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Department ranking for a month or quarter, visible to every signed-in user. Scoring rules live in
 * {@link PerformanceScoreCalculator}; this class loads the data and orders the result.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PerformanceLeaderboardService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final TaskSubmissionRepository submissionRepository;
    private final PerformanceScoreCalculator calculator;
    private final PerformanceProperties properties;
    private final Clock clock;

    /** A resolved period: [from, to] inclusive. */
    public record Period(PerformancePeriod type, LocalDate from, LocalDate to) {
    }

    /**
     * @param departmentId null = the caller's own department (then {@code actor} is required)
     * @param number       month (1-12) or quarter (1-4); null = the current one; ignored for YEARLY
     * @param limit        null = everyone
     */
    @Transactional(readOnly = true)
    public PerformanceLeaderboardResponse leaderboard(UUID departmentId, PerformancePeriod periodType, Integer year,
                                                      Integer number, Integer limit, UserPrincipal actor) {
        UUID deptId = departmentId != null ? departmentId : actor != null ? actor.getDepartmentId() : null;
        if (deptId == null) {
            throw new BadRequestException("You don't belong to a department, so departmentId is required.");
        }
        Department department = departmentRepository.findById(deptId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + deptId));
        LocalDate today = LocalDate.now(clock.withZone(properties.zone()));
        Period period = resolvePeriod(periodType, year, number, today);

        List<User> members = userRepository.findByDepartmentId(deptId).stream()
                .filter(User::isAccountEnabled)
                .toList();
        Map<UUID, List<TaskFacts>> factsByMember = loadFacts(members, period, today);

        Map<UUID, MemberScore> scores = new HashMap<>();
        for (User member : members) {
            scores.put(member.getId(), calculator.score(factsByMember.getOrDefault(member.getId(), List.of()), today));
        }
        OptionalDouble average = scores.values().stream()
                .filter(score -> score.raw() != null)
                .mapToDouble(MemberScore::raw)
                .average();
        Double departmentAverage = average.isPresent() ? average.getAsDouble() : null;

        List<Entry> entries = rank(members, scores);
        if (limit != null && limit > 0 && entries.size() > limit) {
            entries = entries.subList(0, limit);
        }

        log.debug("Leaderboard for department {} {} {}..{}: {} member(s), average {}",
                deptId, period.type(), period.from(), period.to(), members.size(), departmentAverage);
        return PerformanceLeaderboardResponse.builder()
                .departmentId(deptId)
                .departmentName(department.getName())
                .period(period.type())
                .from(period.from())
                .to(period.to())
                .departmentAverage(PerformanceScoreCalculator.round(departmentAverage))
                .minTasksForRating(properties.minTasksForRating())
                .entries(entries)
                .build();
    }

    static Period resolvePeriod(PerformancePeriod type, Integer year, Integer number, LocalDate today) {
        PerformancePeriod periodType = type != null ? type : PerformancePeriod.MONTHLY;
        int y = year != null ? year : today.getYear();
        if (periodType == PerformancePeriod.YEARLY) {
            return new Period(periodType, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        }
        if (periodType == PerformancePeriod.QUARTERLY) {
            int quarter = number != null ? number : (today.getMonthValue() - 1) / 3 + 1;
            if (quarter < 1 || quarter > 4) {
                throw new BadRequestException("quarter must be between 1 and 4.");
            }
            YearMonth first = YearMonth.of(y, (quarter - 1) * 3 + 1);
            return new Period(periodType, first.atDay(1), first.plusMonths(2).atEndOfMonth());
        }
        int month = number != null ? number : today.getMonthValue();
        if (month < 1 || month > 12) {
            throw new BadRequestException("month must be between 1 and 12.");
        }
        YearMonth yearMonth = YearMonth.of(y, month);
        return new Period(periodType, yearMonth.atDay(1), yearMonth.atEndOfMonth());
    }

    /** Each member's tasks that count in the period. */
    private Map<UUID, List<TaskFacts>> loadFacts(List<User> members, Period period, LocalDate today) {
        if (members.isEmpty()) {
            return Map.of();
        }
        Set<UUID> memberIds = members.stream().map(User::getId).collect(Collectors.toSet());
        List<Task> tasks = taskRepository.findActiveForPerformance(memberIds);
        if (tasks.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<TaskSubmission>> submissionsByTask = submissionRepository
                .findByTaskIds(tasks.stream().map(Task::getId).toList()).stream()
                .collect(Collectors.groupingBy(submission -> submission.getTask().getId()));

        Map<UUID, List<TaskFacts>> factsByMember = new HashMap<>();
        for (Task task : tasks) {
            TaskFacts facts = toFacts(task, submissionsByTask.getOrDefault(task.getId(), List.of()));
            if (!calculator.countsIn(facts, period.from(), period.to(), today)) {
                continue;
            }
            for (UUID participant : participants(task)) {
                if (memberIds.contains(participant)) {
                    factsByMember.computeIfAbsent(participant, id -> new ArrayList<>()).add(facts);
                }
            }
        }
        return factsByMember;
    }

    private Set<UUID> participants(Task task) {
        Set<UUID> ids = new LinkedHashSet<>();
        if (task.getAssignee() != null) {
            ids.add(task.getAssignee().getId());
        }
        if (properties.countCollaborators() && task.getAssignments() != null) {
            for (TaskAssignment assignment : task.getAssignments()) {
                if (assignment.getUser() != null) {
                    ids.add(assignment.getUser().getId());
                }
            }
        }
        return ids;
    }

    private TaskFacts toFacts(Task task, List<TaskSubmission> submissions) {
        TaskSubmission first = submissions.stream()
                .filter(submission -> submission.getSubmittedAt() != null)
                .min(Comparator.comparing(TaskSubmission::getSubmittedAt))
                .orElse(null);
        int rejections = (int) submissions.stream()
                .filter(submission -> submission.getStatus() == SubmissionStatus.REJECTED)
                .count();
        // Tasks finished without any submission (older data, admin overrides) are delivered when completed.
        LocalDate delivered = first != null ? date(first.getSubmittedAt()) : date(task.getCompletedAt());
        boolean lateWithReason = first != null && first.isLate() && first.getLateReason() != null;
        return new TaskFacts(task.getPriority(), task.getStatus(), date(task.getCreatedAt()), task.getStartDate(),
                task.getDueDate(), delivered, lateWithReason, rejections);
    }

    private List<Entry> rank(List<User> members, Map<UUID, MemberScore> scores) {
        record Row(User user, MemberScore score, Double adjusted, boolean rated) {
        }
        List<Row> rows = members.stream()
                .map(user -> {
                    MemberScore score = scores.get(user.getId());
                    Double adjusted = calculator.adjust(score.raw(), score.tasksCounted());
                    return new Row(user, score, adjusted, adjusted != null && calculator.isRated(score.tasksCounted()));
                })
                .sorted(Comparator.comparing(Row::rated).reversed()
                        .thenComparing(row -> row.rated() ? -row.adjusted() : 0.0)
                        .thenComparing(row -> -row.score().weightedCompleted())
                        .thenComparing(row -> row.score().onTimeRate() != null ? -row.score().onTimeRate() : 0.0)
                        .thenComparing(row -> row.user().getFullName(), Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        List<Entry> entries = new ArrayList<>();
        int rank = 0;
        for (Row row : rows) {
            MemberScore score = row.score();
            User user = row.user();
            entries.add(Entry.builder()
                    .rank(row.rated() ? ++rank : null)
                    .userId(user.getId())
                    .fullName(user.getFullName())
                    .avatarUrl(user.getAvatarUrl())
                    .positionName(user.getPosition() != null ? user.getPosition().getName() : null)
                    .rated(row.rated())
                    .score(row.adjusted())
                    .stars(row.rated() ? calculator.stars(row.adjusted()) : null)
                    .rawScore(PerformanceScoreCalculator.round(score.raw()))
                    .counts(PerformanceLeaderboardResponse.Counts.builder()
                            .tasks(score.tasksCounted())
                            .completed(score.completed())
                            .onTime(score.onTime())
                            .late(score.late())
                            .rejections(score.rejections())
                            .overdue(score.overdue())
                            .build())
                    .components(PerformanceLeaderboardResponse.Components.builder()
                            .onTime(score.onTimeRate())
                            .quality(score.quality())
                            .completion(score.completionRate())
                            .overdue(score.overdueScore())
                            .speed(score.speed())
                            .build())
                    .build());
        }
        return entries;
    }

    private LocalDate date(Instant instant) {
        return instant != null ? instant.atZone(properties.zone()).toLocalDate() : null;
    }
}
