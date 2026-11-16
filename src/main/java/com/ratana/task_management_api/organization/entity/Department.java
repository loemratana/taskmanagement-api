package com.ratana.task_management_api.organization.entity;

import com.ratana.task_management_api.common.entity.BaseEntity;
import com.ratana.task_management_api.user.entity.User;


import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department extends BaseEntity{

    @Column(nullable = false)
    private String name;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;


    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;


    @Column(columnDefinition = "text")
    private String description;

    @OneToMany(mappedBy="department")
    private List<Position> positions;
}
