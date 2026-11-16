package com.ratana.task_management_api.performance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** One rated person in a frozen ranking; name and photo are copied so the award reads the same later. */
@Entity
@Table(name = "performance_award_entries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceAwardEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "award_id", nullable = false)
    private PerformanceAward award;

    @Column(name = "award_rank", nullable = false)
    private Integer rank;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @Column(name = "position_name")
    private String positionName;

    @Column(name = "score", nullable = false)
    private Double score;

    @Column(name = "stars", nullable = false)
    private Double stars;

    @Column(name = "tasks", nullable = false)
    private Integer tasks;
}
