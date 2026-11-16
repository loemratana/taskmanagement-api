package com.ratana.task_management_api.task.entity;

import com.ratana.task_management_api.user.entity.User;

import com.ratana.task_management_api.task.enums.SubmissionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "task_submissions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_id", nullable = false)
    private User submittedBy;

    /** 1 for the first submission, 2 after the first rejection, ... */
    @Column(nullable = false)
    private Integer round;

    @Column(columnDefinition = "TEXT")
    private String note;

    /** Submitted after the task's due date (business timezone). Snapshot taken at submit time. */
    @Column(name = "is_late", nullable = false)
    @Builder.Default
    private boolean late = false;

    /** The assignee's explanation; required when {@link #late} is true, null otherwise. */
    @Column(name = "late_reason", columnDefinition = "TEXT")
    private String lateReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    private Instant submittedAt;

    /**
     * The department-group message that posted this submission, so the review can reply to it.
     * Read-only for JPA: written only by TaskSubmissionRepository#saveTelegramMessage.
     */
    @Column(name = "telegram_chat_id", insertable = false, updatable = false)
    private String telegramChatId;

    @Column(name = "telegram_message_id", insertable = false, updatable = false)
    private Long telegramMessageId;

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @BatchSize(size = 50)   // loads files for a whole page of submissions in one query
    @Builder.Default
    private List<TaskSubmissionFile> files = new ArrayList<>();

    public void addFile(TaskSubmissionFile file) {
        files.add(file);
        file.setSubmission(this);
    }
}
