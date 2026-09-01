package com.ratana.task_management_api.model;


import com.ratana.task_management_api.enums.Priority;
import com.ratana.task_management_api.enums.TaskStatus;
import jakarta.persistence.*;
import lombok.*;
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

    @OneToMany(mappedBy = "parentTask", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Task> subtasks = new ArrayList<>();

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "start_date")
    private LocalDate startDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;


    @Builder.Default
    private Integer progress = 0; // 0-100

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

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

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

    @OneToOne(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private TaskReminderSetting reminderSetting;

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


}
