package com.ratana.task_management_api.kpi.entity;

import com.ratana.task_management_api.common.entity.BaseEntity;
import com.ratana.task_management_api.organization.entity.Position;

import jakarta.persistence.*;


//@Entity
//@Table(name = "kpi_templates")
public class KpiTemplate extends BaseEntity{

    private String name;
    private String period;          // MONTHLY, QUARTERLY, YEARLY

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private Position position;
}
