package com.ratana.task_management_api.model;

import jakarta.persistence.*;


//@Entity
//@Table(name = "kpi_templates")
public class KpiTemplate extends BaseEnitty{

    private String name;
    private String period;          // MONTHLY, QUARTERLY, YEARLY

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position position;
}
