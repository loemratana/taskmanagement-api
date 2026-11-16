package com.ratana.task_management_api.file.exception;

import com.ratana.task_management_api.common.exception.ResourceNotFoundException;

/** A ResourceNotFoundException, so the global handler maps it to 404. */
public class AttachmentNotFoundException extends ResourceNotFoundException {
    public AttachmentNotFoundException(String message) {
        super(message);
    }
}
