package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.dto.request.DepartmentTelegramGroupRequest;
import com.ratana.task_management_api.dto.response.DepartmentTelegramGroupResponse;
import com.ratana.task_management_api.exception.BadRequestException;
import com.ratana.task_management_api.exception.DuplicateResourceException;
import com.ratana.task_management_api.exception.ResourceNotFoundException;
import com.ratana.task_management_api.mapper.DepartmentTelegramGroupMapper;
import com.ratana.task_management_api.model.Department;
import com.ratana.task_management_api.model.DepartmentTelegramGroup;
import com.ratana.task_management_api.repository.DepartmentRepository;
import com.ratana.task_management_api.repository.DepartmentTelegramGroupRepository;
import com.ratana.task_management_api.service.DepartmentTelegramGroupService;
import com.ratana.task_management_api.service.TelegramBotClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DepartmentTelegramGroupServiceImpl implements DepartmentTelegramGroupService {

    private final DepartmentTelegramGroupRepository groupRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentTelegramGroupMapper mapper;
    private final TelegramBotClient telegramBotClient;

    @Override
    @Transactional
    public DepartmentTelegramGroupResponse create(DepartmentTelegramGroupRequest request) {
        if (groupRepository.existsByDepartmentId(request.getDepartmentId())) {
            throw new DuplicateResourceException(
                    "A Telegram group is already configured for department: " + request.getDepartmentId());
        }

        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department not found with id: " + request.getDepartmentId()));

        requireBotCanAccessChat(request.getChatId());

        DepartmentTelegramGroup group = mapper.toEntity(request, department);
        group.setVerifiedAt(Instant.now());

        DepartmentTelegramGroup saved = groupRepository.save(group);
        log.info("Telegram group configured for department {} ({})", department.getName(), department.getId());

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DepartmentTelegramGroupResponse update(UUID id, DepartmentTelegramGroupRequest request) {
        DepartmentTelegramGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Telegram group mapping not found: " + id));

        if (!group.getDepartment().getId().equals(request.getDepartmentId())) {
            throw new BadRequestException(
                    "Department cannot be changed on an existing mapping — deactivate this one and create a new mapping instead.");
        }

        if (!Objects.equals(group.getChatId(), request.getChatId())) {
            requireBotCanAccessChat(request.getChatId());
            group.setVerifiedAt(Instant.now());
        }

        mapper.updateEntityFromRequest(request, group);

        DepartmentTelegramGroup saved = groupRepository.save(group);
        log.info("Telegram group mapping updated: {} ({})", saved.getDepartment().getName(), saved.getId());

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DepartmentTelegramGroupResponse getByDepartment(UUID departmentId) {
        return mapper.toResponse(groupRepository.findByDepartmentId(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No Telegram group configured for department: " + departmentId)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentTelegramGroupResponse> getAll() {
        return mapper.toResponseList(groupRepository.findAll());
    }

    @Override
    @Transactional
    public void deactivate(UUID id) {
        DepartmentTelegramGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Telegram group mapping not found: " + id));
        group.setActive(false);
        groupRepository.save(group);
        log.info("Telegram group mapping deactivated: {} ({})", group.getDepartment().getName(), id);
    }

    @Override
    @Transactional(readOnly = true)
    public void sendTestMessage(UUID id) {
        DepartmentTelegramGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Telegram group mapping not found: " + id));
        telegramBotClient.sendMessage(group.getChatId(),
                "✅ <b>TMS test message</b>\nThis group is correctly configured for " + group.getDepartment().getName() + ".");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DepartmentTelegramGroup> resolveActive(UUID departmentId) {
        return groupRepository.findByDepartmentIdAndActiveTrue(departmentId);
    }

    private void requireBotCanAccessChat(String chatId) {
        if (!telegramBotClient.verifyChat(chatId)) {
            throw new BadRequestException(
                    "The bot cannot access chat " + chatId + " — make sure it has been added to the group.");
        }
    }
}
