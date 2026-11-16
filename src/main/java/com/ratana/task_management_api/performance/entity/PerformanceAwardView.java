package com.ratana.task_management_api.performance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A user closed an award's popup; it is no longer pending for them. */
@Entity
@Table(name = "performance_award_views")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceAwardView {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "award_id", nullable = false)
    private UUID awardId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "seen_at", nullable = false)
    private Instant seenAt;
}
