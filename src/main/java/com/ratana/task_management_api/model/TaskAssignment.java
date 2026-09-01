package com.ratana.task_management_api.model;


import com.ratana.task_management_api.enums.AssignmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name="task_assignments",
        uniqueConstraints={
                @UniqueConstraint(
                        columnNames={
                                "task_id",
                                "user_id"
                        }
                )
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;



    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name="task_id",
            nullable=false
    )
    private Task task;

    // employee
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name="user_id",
            nullable=false
    )
    private User user;

    // manager/supervisor
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name="assigned_by_id"
    )
    private User assignedBy;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AssignmentStatus status =
            AssignmentStatus.ASSIGNED;

    private Instant assignedAt;

    private Instant completedAt;
}
