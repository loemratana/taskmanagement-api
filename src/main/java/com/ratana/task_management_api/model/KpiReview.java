package com.ratana.task_management_api.model;


import com.ratana.task_management_api.enums.ReviewStatus;
import jakarta.persistence.*;

//@Entity
//@Table(name = "kpi_templates")
public class KpiReview  extends BaseEnitty{
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private KpiTemplate template;

    private String reviewPeriod;
    private Double selfScore;
    private Double managerScore;
    private Double finalScore;

    @Enumerated(EnumType.STRING)
    private ReviewStatus status = ReviewStatus.DRAFT;

    private String managerComment;
}
