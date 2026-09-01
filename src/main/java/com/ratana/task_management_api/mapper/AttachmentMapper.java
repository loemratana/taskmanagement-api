package com.ratana.task_management_api.mapper;


import com.ratana.task_management_api.dto.response.AttachmentResponse;
import com.ratana.task_management_api.model.Attachment;
import org.springframework.stereotype.Component;

@Component
public class AttachmentMapper {


    public AttachmentResponse toResponse(Attachment attachment) {

        String uploaderName = attachment.getUser() != null? attachment.getUser().getFullName() : null;
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getOriginalName(),
                attachment.getFileType(),
                attachment.getFileSize(),
                "/api/v1/attachments/%s".formatted(attachment.getId()),
                attachment.getUser().getId(),
                uploaderName,
                attachment.getTask().getId(),
                attachment.getCreatedAt()
        );
    }
}
