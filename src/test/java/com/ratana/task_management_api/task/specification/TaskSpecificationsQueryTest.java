package com.ratana.task_management_api.task.specification;

import com.ratana.task_management_api.task.dto.request.TaskFilterRequest;
import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the filter against the real database (read-only): the SQL must be valid, and for every
 * assignee it must keep exactly the rows the app's "My tasks" list shows.
 */
@SpringBootTest
@Transactional(readOnly = true)
class TaskSpecificationsQueryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Test
    @DisplayName("excludeNestedSubtasks keeps a subtask unless its parent is also the assignee's task")
    void excludeNestedSubtasks_matchesListRowRule() {
        // A user with no tasks: the query must still run.
        assertThat(taskRepository.count(spec(UUID.randomUUID()))).isZero();

        List<UUID> assignees = taskRepository.findAll().stream()
                .filter(task -> task.getAssignee() != null)
                .map(task -> task.getAssignee().getId())
                .distinct()
                .limit(20)
                .toList();
        for (UUID assigneeId : assignees) {
            List<Task> mine = taskRepository.findAll(TaskSpecifications.withAssignee(assigneeId));
            long expectedRows = mine.stream()
                    .filter(task -> task.getParentTask() == null || !task.getParentTask().isAssignee(assigneeId))
                    .count();
            assertThat(taskRepository.count(spec(assigneeId)))
                    .as("rows for assignee %s", assigneeId)
                    .isEqualTo(expectedRows);
        }
    }

    private static Specification<Task> spec(UUID assigneeId) {
        TaskFilterRequest filter = new TaskFilterRequest();
        filter.setAssigneeId(assigneeId);
        filter.setExcludeNestedSubtasks(true);
        return TaskSpecifications.fromFilter(filter);
    }
}
