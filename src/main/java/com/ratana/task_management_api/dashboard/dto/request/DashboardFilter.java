package com.ratana.task_management_api.dashboard.dto.request;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardFilter {

    private UUID managerId;
    private UUID departmentId;
    private UUID employeeId;

    private LocalDate startDate;
    private LocalDate endDate;

    public  static DashboardFilter empty()
    {
        return  new DashboardFilter();
    }
}
