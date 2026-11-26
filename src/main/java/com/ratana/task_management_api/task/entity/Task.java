package com.ratana.task_management_api.task.entity;

import com.ratana.task_management_api.file.entity.Attachment;
import com.ratana.task_management_api.organization.entity.Department;
import com.ratana.task_management_api.user.entity.User;


import com.ratana.task_management_api.task.enums.Priority;
import com.ratana.task_management_api.task.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue(generator = "uuid2")
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Version
    @Builder.Default
    private Long version = 0L;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    @Builder.Default
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_task_id")
    private Task parentTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_id")
    private User assignedBy;


    // multiple employees
    @OneToMany(
            mappedBy="task",
            cascade=CascadeType.ALL,
            orphanRemoval=true
    )
    @Builder.Default
    private List<TaskAssignment> assignments =
            new ArrayList<>();

    // Task lists read every row's subtasks to compute progress; batching turns N lazy loads into a few IN queries.
    @OneToMany(mappedBy = "parentTask", cascade = CascadeType.ALL)
    @BatchSize(size = 50)
    @Builder.Default
    private List<Task> subtasks = new ArrayList<>();

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;


    @Column(name = "estimated_hours", precision = 6, scale = 2)
    private BigDecimal estimatedHours;

    @Column(name = "actual_hours", precision = 6, scale = 2)
    private BigDecimal actualHours;


    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")

    private Set<String> tags; // or List<String>

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    /**
     * A task given to several people: it has no assignee, each person works on their own member
     * subtask, and its status is derived from theirs by TaskGroupService (never set by hand).
     */
    @Column(name = "is_group_task", nullable = false)
    @Builder.Default
    private boolean groupTask = false;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    /** When the task last became DONE/APPROVED; null while not completed. Maintained by {@link #setStatus}. */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * The department-group message that announced this task's (latest) assignment, so the
     * submission can be posted as a reply to it. Read-only for JPA: written only by
     * TaskRepository#saveTelegramMessage, so saving a task loaded earlier can't wipe them
     * and recording them never bumps @Version.
     */
    @Column(name = "telegram_chat_id", insertable = false, updatable = false)
    private String telegramChatId;

    @Column(name = "telegram_message_id", insertable = false, updatable = false)
    private Long telegramMessageId;

    @OneToMany(
            mappedBy="task",
            cascade=CascadeType.ALL,
            orphanRemoval=true
    )
    @Builder.Default
    private List<Attachment> attachments =
            new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default

    private List<TaskComment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TaskLog> logs = new ArrayList<>();

    // No inverse mapping to TaskReminderSetting on purpose: Hibernate can't lazy-load the non-owning
    // side of a @OneToOne, so it ran one extra SELECT for every task loaded (lists, dashboard, jobs).
    // Load a task's setting through TaskReminderSettingRepository instead.

    /**
     * Every status change goes through here, so completedAt can't drift: stamped on entering
     * DONE/APPROVED (APPROVED → DONE keeps the original time), cleared when the task is reopened.
     */
    public void setStatus(TaskStatus status) {
        boolean wasCompleted = isCompletedStatus(this.status);
        boolean nowCompleted = isCompletedStatus(status);
        if (nowCompleted && !wasCompleted) {
            completedAt = Instant.now();
        } else if (!nowCompleted) {
            completedAt = null;
        }
        this.status = status;
    }

    /** Builder-created tasks bypass setStatus; cover a task created already completed. */
    @PrePersist
    void stampCompletedAtOnCreate() {
        if (isCompletedStatus(status) && completedAt == null) {
            completedAt = Instant.now();
        }
    }

    private static boolean isCompletedStatus(TaskStatus status) {
        return status == TaskStatus.DONE || status == TaskStatus.APPROVED;
    }

    /** True if user is the primary assignee or listed in multi-assignee assignments. */
    public boolean isAssignee(UUID userId) {
        if (userId == null) {
            return false;
        }
        if (assignee != null && userId.equals(assignee.getId())) {
            return true;
        }
        return assignments != null && assignments.stream()
                .anyMatch(a -> a.getUser() != null && userId.equals(a.getUser().getId()));
    }


    public void addAssignment(TaskAssignment assignment) {
        assignments.add(assignment);
        assignment.setTask(this);
    }

    /**
     * 0–100, derived from status. Approved/done tasks are 100 and cancelled ones 0; otherwise a task
     * with active, non-cancelled subtasks shows the average of their status weights, and a task without
     * them shows its own status weight. Must run inside a transaction — it reads the lazy {@code subtasks}.
     */
    public int computeProgress() {
        if (status == null) {
            return 0;
        }
        if (isCompletedStatus(status) || status == TaskStatus.CANCELLED) {
            return status.progressWeight();
        }

        List<Task> countable = countableSubtasks();
        if (countable.isEmpty()) {
            return status.progressWeight();
        }
        double average = countable.stream()
                .mapToInt(subtask -> subtask.getStatus().progressWeight())
                .average()
                .orElse(0);
        return (int) Math.round(average);
    }

    /**
     * Subtasks that count toward progress (and, for a group task, toward its status): active and
     * not cancelled. Must run inside a transaction — it reads the lazy {@code subtasks}.
     */
    public List<Task> countableSubtasks() {
        return subtasks == null
                ? List.of()
                : subtasks.stream()
                        .filter(Task::isActive)
                        .filter(subtask -> subtask.getStatus() != null
                                && subtask.getStatus() != TaskStatus.CANCELLED)
                        .toList();
    }


}
