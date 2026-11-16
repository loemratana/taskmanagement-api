package com.ratana.task_management_api.task.reminder;

import com.ratana.task_management_api.notification.dto.request.NotificationRequest;
import com.ratana.task_management_api.notification.enums.NotificationType;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.entity.TaskAssignment;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.reminder.TaskOverdueNotifier.OverdueTask;
import com.ratana.task_management_api.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TaskOverdueNotifierTest {

    private static User user(String name) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName(name);
        return user;
    }

    @Test
    @DisplayName("Assignee and collaborators are alerted; the assignee's manager watches")
    void toOverdueTask_peopleAndWatcher() {
        User manager = user("Boss");
        User assignee = user("Dara");
        assignee.setManager(manager);
        User collaborator = user("Sok");
        Task task = Task.builder().id(UUID.randomUUID()).title("Report").status(TaskStatus.IN_PROGRESS)
                .dueDate(LocalDate.of(2026, 11, 15)).assignee(assignee).creator(user("Creator"))
                .assignments(new ArrayList<>()).build();
        task.getAssignments().add(TaskAssignment.builder().task(task).user(collaborator).build());

        OverdueTask overdue = TaskOverdueNotifier.toOverdueTask(task);

        assertThat(overdue.people()).containsExactly(assignee.getId(), collaborator.getId());
        assertThat(overdue.watcherId()).isEqualTo(manager.getId());
    }

    @Test
    @DisplayName("Without a manager the creator watches; nobody watches their own task")
    void toOverdueTask_creatorFallback() {
        User assignee = user("Dara");
        User creator = user("Creator");
        Task task = Task.builder().id(UUID.randomUUID()).title("Report").status(TaskStatus.TODO)
                .dueDate(LocalDate.of(2026, 11, 15)).assignee(assignee).creator(creator).build();
        assertThat(TaskOverdueNotifier.toOverdueTask(task).watcherId()).isEqualTo(creator.getId());

        Task selfMade = Task.builder().id(UUID.randomUUID()).title("Mine").status(TaskStatus.TODO)
                .dueDate(LocalDate.of(2026, 11, 15)).assignee(assignee).creator(assignee).build();
        assertThat(TaskOverdueNotifier.toOverdueTask(selfMade).watcherId()).isNull();
    }

    @Test
    @DisplayName("Each manager gets one summary listing up to three tasks")
    void managerSummary_onePerManager() {
        UUID boss = UUID.randomUUID();
        UUID otherBoss = UUID.randomUUID();
        List<OverdueTask> overdue = List.of(
                overdue("Report", "Dara", boss), overdue("Slides", "Sok", boss),
                overdue("Budget", "Vy", boss), overdue("Audit", "Lina", boss),
                overdue("Print", "Kim", otherBoss));

        Map<UUID, List<OverdueTask>> byWatcher = TaskOverdueNotifier.byWatcher(overdue);
        assertThat(byWatcher).containsOnlyKeys(boss, otherBoss);

        NotificationRequest summary = TaskOverdueNotifier.managerSummary(boss, byWatcher.get(boss));
        assertThat(summary.getTitle()).isEqualTo("⚠️ 4 team tasks became overdue");
        assertThat(summary.getMessage()).isEqualTo("Dara: Report · Sok: Slides · Vy: Budget · +1 more");
        assertThat(summary.getType()).isEqualTo(NotificationType.TASK_OVERDUE);
        assertThat(summary.getReferenceType()).isEqualTo("OVERDUE_TASKS");

        NotificationRequest single = TaskOverdueNotifier.managerSummary(otherBoss, byWatcher.get(otherBoss));
        assertThat(single.getReferenceType()).isEqualTo("TASK");
        assertThat(single.getReferenceId()).isEqualTo(byWatcher.get(otherBoss).get(0).taskId());
    }

    @Test
    @DisplayName("Submitted (in review) tasks aren't alerted: they wait on the reviewer")
    void owesWork_excludesInReview() {
        assertThat(TaskOverdueNotifier.OWES_WORK).doesNotContain(TaskStatus.IN_REVIEW, TaskStatus.DONE);
    }

    private static OverdueTask overdue(String title, String assignee, UUID watcher) {
        return new OverdueTask(UUID.randomUUID(), title, LocalDate.of(2026, 11, 15), null, assignee,
                Set.of(UUID.randomUUID()), watcher);
    }
}
