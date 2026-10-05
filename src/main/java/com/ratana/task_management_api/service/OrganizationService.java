package com.ratana.task_management_api.service;

import com.ratana.task_management_api.dto.response.OrganizationTreeNodeResponse;

import java.util.List;

public interface OrganizationService {

    /**
     * The complete reporting hierarchy, rooted at every top-level employee
     * (manager_id IS NULL). Built from a single recursive query.
     */
    List<OrganizationTreeNodeResponse> getTree();
}
