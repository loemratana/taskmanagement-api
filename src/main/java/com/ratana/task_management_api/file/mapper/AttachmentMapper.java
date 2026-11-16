package com.ratana.task_management_api.file.mapper;


import com.ratana.task_management_api.file.dto.response.AttachmentResponse;
import com.ratana.task_management_api.file.entity.Attachment;
import org.springframework.stereotype.Component;

@Component
public class AttachmentMapper {


    /** downloadUrl is a signed storage URL the client can open or render directly. */
    public AttachmentResponse toResponse(Attachment attachment, String downloadUrl) {

        boolean hasUser = attachment.getUser() != null;
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getOriginalName(),
                attachment.getFileType(),
                attachment.getFileSize(),
                downloadUrl,
                hasUser ? attachment.getUser().getId() : null,
                hasUser ? attachment.getUser().getFullName() : null,
                attachment.getTask().getId(),
                attachment.getCreatedAt()
        );
    }
}
