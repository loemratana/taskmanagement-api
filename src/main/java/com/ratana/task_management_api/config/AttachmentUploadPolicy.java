package com.ratana.task_management_api.config;

import java.util.Set;

public class AttachmentUploadPolicy {

    private AttachmentUploadPolicy() {

    }

    public static  final  long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024; //20MB
    public static  final Set<String> ALLOWED_EXACT_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/zip"
    );
    /** image/* is allowed via prefix match; everything else must be an exact allow-listed type. */

    public static  boolean isAllowed(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return false;
        }

        if (mimeType.startsWith("image/")) {
            return true;
        }

        return ALLOWED_EXACT_MIME_TYPES.contains(mimeType);
    }


}
