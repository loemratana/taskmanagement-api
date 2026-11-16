package com.ratana.task_management_api.file.service;


import com.ratana.task_management_api.file.dto.response.AttachmentResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface AttachmentService {

    AttachmentResponse upload(UUID taskId, MultipartFile file);
    PageResponse<AttachmentResponse> listForTask(UUID taskId, Pageable pageable);
    String download(UUID attachmentId);
    void delete(UUID id);
}
