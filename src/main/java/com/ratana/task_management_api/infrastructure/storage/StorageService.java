package com.ratana.task_management_api.infrastructure.storage;

import com.ratana.task_management_api.file.dto.response.FileUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

public interface StorageService {

    /** Uploads into a custom directory, e.g. "avatars", "tasks/42", "reports/2026/10". */
    FileUploadResponse upload(MultipartFile file, String directory);

    /** Uploads raw bytes under an exact key (used by AttachmentServiceImpl). */
    void upload(String storageKey, byte[] content, String contentType);

    void delete(String storageKey);

    /** Reads a stored object fully into memory (used to forward submission files to Telegram). */
    byte[] download(String storageKey);

    /** Generates a time-limited URL for downloading a private object. */
    String createSignedUrl(String storageKey, Duration expiresIn);

    /** Only valid if the bucket is configured as public. */
    String publicUrl(String storageKey);
}
