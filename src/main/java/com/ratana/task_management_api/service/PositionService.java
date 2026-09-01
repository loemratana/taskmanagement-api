package com.ratana.task_management_api.service;

import com.ratana.task_management_api.dto.request.PositionRequest;
import com.ratana.task_management_api.dto.response.PositionResponse;

import java.util.List;
import java.util.UUID;

public interface PositionService {

    PositionResponse create(PositionRequest request);

    PositionResponse update(UUID id, PositionRequest request);

    PositionResponse getById(UUID id);

    List<PositionResponse> getAll();

    void delete(UUID id);
}
