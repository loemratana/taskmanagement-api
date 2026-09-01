package com.ratana.task_management_api.model;


import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department extends BaseEnitty{

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
