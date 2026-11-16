package com.ratana.task_management_api.task.reminder;

import com.ratana.task_management_api.notification.dto.request.NotificationRequest;
import com.ratana.task_management_api.notification.enums.NotificationPriority;
import com.ratana.task_management_api.notification.enums.NotificationType;
import com.ratana.task_management_api.notification.service.NotificationService;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.entity.TaskAssignment;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.repository.TaskRepository;
import com.ratana.task_management_api.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Every morning, tells people about tasks that became overdue overnight (due yesterday, still not
 * submitted): a push to each person on the task, and one summary per manager listing their team's
 * newly overdue tasks. Each task is alerted once, the day after its due date; the regular reminders
 * keep nagging the assignee after that.
 */
@Slf4j
@Component
public class TaskOverdueNotifier {

    /** Still owes work. IN_REVIEW is excluded: it was submitted and waits on the reviewer, not the assignee. */
    static final Set<TaskStatus> OWES_WORK =
            EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.REJECTED, TaskStatus.BLOCKED);
    static final int SUMMARY_LISTED = 3;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final TransactionTemplate readOnly;
    private final Clock clock;
    private final ZoneId zone;

    /** A task that just became overdue, read out of the database before anything is sent. */
    record OverdueTask(UUID taskId, String title, LocalDate dueDate, UUID senderId, String assigneeName,
                       Set<UUID> people, UUID watcherId) {
    }

    public TaskOverdueNotifier(TaskRepository taskRepository, NotificationService notificationService,
                               PlatformTransactionManager transactionManager, Clock clock,
                               @Value("${app.reminder.default-timezone:Asia/Phnom_Penh}") String zone) {
        this.taskRepository = taskRepository;
        this.notificationService = notificationService;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    @Scheduled(cron = "${app.reminder.overdue-cron:0 0 9 * * *}", zone = "${app.reminder.default-timezone:Asia/Phnom_Penh}")
    public void notifyNewlyOverdue() {
        LocalDate yesterday = LocalDate.now(clock.withZone(zone)).minusDays(1);
        List<OverdueTask> overdue = readOnly.execute(status ->
                taskRepository.findActiveDueOnWithStatus(yesterday, OWES_WORK).stream()
                        .map(TaskOverdueNotifier::toOverdueTask)
                        .toList());
        if (overdue == null || overdue.isEmpty()) {
            log.debug("No task became overdue today (due {})", yesterday);
            return;
        }

        int sent = 0;
        for (OverdueTask task : overdue) {
            for (UUID personId : task.people()) {
                sent += send(NotificationRequest.builder()
                        .recipientId(personId)
                        .senderId(task.senderId() != null ? task.senderId() : personId)
                        .title("⚠️ Task Overdue")
                        .message("\"" + task.title() + "\" was due " + task.dueDate().format(DATE)
                                + " and isn't submitted yet. Submit it with the reason it's late.")
                        .type(NotificationType.TASK_OVERDUE)
                        .priority(NotificationPriority.HIGH)
                        .referenceType("TASK")
                        .referenceId(task.taskId())
                        .metadata(Map.of("dueDate", task.dueDate().toString()))
                        .build());
            }
        }
        for (Map.Entry<UUID, List<OverdueTask>> entry : byWatcher(overdue).entrySet()) {
            sent += send(managerSummary(entry.getKey(), entry.getValue()));
        }
        log.info("Overdue alerts: {} task(s) due {} became overdue, {} notification(s) sent",
                overdue.size(), yesterday, sent);
    }

    static OverdueTask toOverdueTask(Task task) {
        Set<UUID> people = new LinkedHashSet<>();
        people.add(task.getAssignee().getId());
        if (task.getAssignments() != null) {
            task.getAssignments().stream()
                    .map(TaskAssignment::getUser)
                    .filter(Objects::nonNull)
                    .map(User::getId)
                    .forEach(people::add);
        }
        // The assignee's manager watches the team; without one, whoever created the task.
        User watcher = task.getAssignee().getManager() != null ? task.getAssignee().getManager() : task.getCreator();
        UUID watcherId = watcher != null && !people.contains(watcher.getId()) ? watcher.getId() : null;
        User sender = task.getAssignedBy() != null ? task.getAssignedBy() : task.getCreator();
        return new OverdueTask(task.getId(), task.getTitle(), task.getDueDate(),
                sender != null ? sender.getId() : null, task.getAssignee().getFullName(), people, watcherId);
    }

    static Map<UUID, List<OverdueTask>> byWatcher(List<OverdueTask> overdue) {
        return overdue.stream()
                .filter(task -> task.watcherId() != null)
                .collect(Collectors.groupingBy(OverdueTask::watcherId, LinkedHashMap::new, Collectors.toList()));
    }

    /** One push per manager: "3 tasks in your team became overdue: Dara: Report · …". Opens the task when there's one. */
    static NotificationRequest managerSummary(UUID managerId, List<OverdueTask> tasks) {
        String listed = tasks.stream()
                .limit(SUMMARY_LISTED)
                .map(task -> task.assigneeName() + ": " + task.title())
                .collect(Collectors.joining(" · "));
        int more = tasks.size() - SUMMARY_LISTED;
        boolean single = tasks.size() == 1;
        List<String> taskIds = new ArrayList<>(tasks.stream().map(task -> task.taskId().toString()).toList());
        return NotificationRequest.builder()
                .recipientId(managerId)
                .senderId(managerId)
                .title(single ? "⚠️ A team task became overdue" : "⚠️ " + tasks.size() + " team tasks became overdue")
                .message(listed + (more > 0 ? " · +" + more + " more" : ""))
                .type(NotificationType.TASK_OVERDUE)
                .priority(NotificationPriority.HIGH)
                // One task opens it; several open the overdue task list.
                .referenceType(single ? "TASK" : "OVERDUE_TASKS")
                .referenceId(single ? tasks.get(0).taskId() : null)
                .metadata(Map.of("taskIds", taskIds))
                .build();
    }

    private int send(NotificationRequest request) {
        try {
            notificationService.send(request);
            return 1;
        } catch (RuntimeException e) {
            log.warn("Overdue alert to {} failed: {}", request.getRecipientId(), e.getMessage());
            return 0;
        }
    }
}
