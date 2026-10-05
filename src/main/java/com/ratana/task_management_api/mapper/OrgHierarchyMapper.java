package com.ratana.task_management_api.mapper;

import com.ratana.task_management_api.dto.response.OrgMemberResponse;
import com.ratana.task_management_api.dto.response.OrganizationTreeNodeResponse;
import com.ratana.task_management_api.dto.response.Summary;
import com.ratana.task_management_api.repository.projection.OrgHierarchyRow;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Assembles the flat, path-ordered rows returned by the recursive (WITH RECURSIVE)
 * hierarchy queries into response DTOs. Each query already does the recursion and
 * join work in one round trip; this class only reshapes the result — no further
 * queries are issued here, so building even a large tree stays N+1-free.
 */
@Component
public class OrgHierarchyMapper {

    public OrgMemberResponse toMember(OrgHierarchyRow row) {
        return OrgMemberResponse.builder()
                .id(row.getId())
                .fullName(row.getFullName())
                .email(row.getEmail())
                .avatarUrl(row.getAvatarUrl())
                .position(toSummary(row.getPositionId(), row.getPositionName()))
                .department(toSummary(row.getDepartmentId(), row.getDepartmentName()))
                .managerId(row.getManagerId())
                .level(row.getLevel())
                .build();
    }

    public List<OrgMemberResponse> toMembers(List<OrgHierarchyRow> rows) {
        return rows.stream().map(this::toMember).toList();
    }

    /**
     * Assembles path-ordered rows into a forest: a row whose managerId is null, or
     * whose manager isn't present in this row set (e.g. the excluded root of a
     * subtree query), becomes a top-level node of the returned list.
     */
    public List<OrganizationTreeNodeResponse> toTree(List<OrgHierarchyRow> rows) {
        Map<UUID, OrganizationTreeNodeResponse> nodesById = new LinkedHashMap<>();
        List<OrganizationTreeNodeResponse> roots = new java.util.ArrayList<>();

        for (OrgHierarchyRow row : rows) {
            OrganizationTreeNodeResponse node = OrganizationTreeNodeResponse.builder()
                    .id(row.getId())
                    .fullName(row.getFullName())
                    .email(row.getEmail())
                    .avatarUrl(row.getAvatarUrl())
                    .position(toSummary(row.getPositionId(), row.getPositionName()))
                    .department(toSummary(row.getDepartmentId(), row.getDepartmentName()))
                    .level(row.getLevel())
                    .build();
            nodesById.put(node.getId(), node);

            OrganizationTreeNodeResponse parent = row.getManagerId() == null ? null : nodesById.get(row.getManagerId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    private Summary toSummary(UUID id, String name) {
        return id == null ? null : new Summary(id, name);
    }
}
