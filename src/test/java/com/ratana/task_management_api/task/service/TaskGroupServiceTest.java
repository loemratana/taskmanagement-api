package com.ratana.task_management_api.task.service;

import com.ratana.task_management_api.task.entity.Task;
import com.ratana.task_management_api.task.enums.TaskAuditAction;
import com.ratana.task_management_api.task.enums.TaskStatus;
import com.ratana.task_management_api.task.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TaskGroupServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskAuditService taskAuditService;
    @InjectMocks private TaskGroupService service;

    private final UUID actorId = UUID.randomUUID();

    @Test
    @DisplayName("every member approved/done -> group closes as DONE, with 100% progress")
    void allApproved_closesGroup() {
        Task group = group(TaskStatus.IN_PROGRESS, member(TaskStatus.APPROVED), member(TaskStatus.DONE));

        service.sync(group, actorId);

        assertThat(group.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(group.getCompletedAt()).isNotNull();
        assertThat(group.computeProgress()).isEqualTo(100);
        verify(taskRepository).save(group);
        verify(taskAuditService).record(eq(group), eq(actorId), eq(TaskAuditAction.GROUP_STATUS_SYNCED),
                any(), any(), anyString());
    }

    @Test
    @DisplayName("one member submitted -> group IN_PROGRESS, progress is the members' average")
    void someStarted_inProgress() {
        Task group = group(TaskStatus.TODO,
                member(TaskStatus.IN_REVIEW), member(TaskStatus.TODO), member(TaskStatus.TODO), member(TaskStatus.DONE));

        service.sync(group, actorId);

        assertThat(group.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(group.computeProgress()).isEqualTo(38);   // (50 + 0 + 0 + 100) / 4 = 37.5
    }

    @Test
    @DisplayName("cancelled and archived members don't block closing")
    void cancelledAndArchived_ignored() {
        Task archived = member(TaskStatus.TODO);
        archived.setActive(false);
        Task group = group(TaskStatus.IN_PROGRESS,
                member(TaskStatus.APPROVED), member(TaskStatus.CANCELLED), archived);

        service.sync(group, actorId);

        assertThat(group.getStatus()).isEqualTo(TaskStatus.DONE);
    }

    @Test
    @DisplayName("a member reopened after the group closed reopens the group")
    void memberReopened_reopensGroup() {
        Task group = group(TaskStatus.DONE, member(TaskStatus.DONE), member(TaskStatus.TODO));

        service.sync(group, actorId);

        assertThat(group.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(group.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("every member cancelled -> group CANCELLED")
    void allCancelled_cancelsGroup() {
        Task group = group(TaskStatus.IN_PROGRESS, member(TaskStatus.CANCELLED), member(TaskStatus.CANCELLED));

        service.sync(group, actorId);

        assertThat(group.getStatus()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    @DisplayName("status already right -> nothing saved or audited")
    void unchanged_noWrite() {
        Task group = group(TaskStatus.IN_PROGRESS, member(TaskStatus.IN_REVIEW), member(TaskStatus.TODO));

        service.sync(group, actorId);

        verify(taskRepository, never()).save(any());
        verifyNoInteractions(taskAuditService);
    }

    @Test
    @DisplayName("a member of a normal (non-group) parent never changes the parent")
    void nonGroupParent_untouched() {
        Task parent = group(TaskStatus.IN_PROGRESS, member(TaskStatus.DONE));
        parent.setGroupTask(false);

        service.onMemberChanged(parent.getSubtasks().get(0), actorId);

        assertThat(parent.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        verifyNoInteractions(taskRepository, taskAuditService);
    }

    private static Task group(TaskStatus status, Task... members) {
        Task group = Task.builder().id(UUID.randomUUID()).groupTask(true)
                .subtasks(new ArrayList<>(Arrays.asList(members))).build();
        group.setStatus(status);
        for (Task member : members) {
            member.setParentTask(group);
        }
        return group;
    }

    private static Task member(TaskStatus status) {
        Task member = Task.builder().id(UUID.randomUUID()).build();
        member.setStatus(status);
        return member;
    }
}
