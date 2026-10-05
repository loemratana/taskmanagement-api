package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.dto.response.OrganizationTreeNodeResponse;
import com.ratana.task_management_api.mapper.OrgHierarchyMapper;
import com.ratana.task_management_api.repository.UserRepository;
import com.ratana.task_management_api.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final UserRepository userRepository;
    private final OrgHierarchyMapper orgHierarchyMapper;

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationTreeNodeResponse> getTree() {
        return orgHierarchyMapper.toTree(userRepository.findOrganizationTree());
    }
}
