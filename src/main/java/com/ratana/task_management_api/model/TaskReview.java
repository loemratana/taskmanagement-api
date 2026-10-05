package com.ratana.task_management_api.model;

import com.ratana.task_management_api.enums.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The rating + comment a reviewer (assigner or department manager) leaves when
 * approving or rejecting a task's submitted work. One row per task — a re-review
 * overwrites the existing rating/comment rather than adding a new row.
 */
@Entity
@Table(name = "task_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskReview extends BaseEnitty {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false, unique = true)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @Column(nullable = false)
    private Integer rating;

    @Column(nullable = false, columnDefinition = "text")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus decision;
}
