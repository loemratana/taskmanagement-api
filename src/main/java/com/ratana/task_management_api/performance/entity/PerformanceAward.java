package com.ratana.task_management_api.performance.entity;

import com.ratana.task_management_api.performance.enums.PerformancePeriod;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A department's ranking for one period, frozen when it was announced. */
@Entity
@Table(name = "performance_awards")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceAward {

    /** Stored in period_number for a yearly award (the unique key can't hold NULL). */
    public static final int YEARLY_NUMBER = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "department_name", nullable = false)
    private String departmentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false, length = 20)
    private PerformancePeriod period;

    @Column(name = "period_year", nullable = false)
    private Integer periodYear;

    @Column(name = "period_number", nullable = false)
    private Integer periodNumber;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @OneToMany(mappedBy = "award", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rank ASC")
    @Builder.Default
    private List<PerformanceAwardEntry> entries = new ArrayList<>();

    public void addEntry(PerformanceAwardEntry entry) {
        entry.setAward(this);
        entries.add(entry);
    }
}
