package com.ratana.task_management_api.user.service;

import com.ratana.task_management_api.user.dto.response.UserPerformanceResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface UserPerformanceService {
    UserPerformanceResponse getPerformance(UUID userId, LocalDate from, LocalDate to);
}
