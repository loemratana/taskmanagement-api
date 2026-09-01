package com.ratana.task_management_api.service;

import java.time.Duration;

public interface StorageService {

    void upload(String storageKey, byte[] content, String contentType);

    void delete(String storageKey);

    /** Generates a time-limited URL for downloading a private object. */
    String createSignedUrl(String storageKey, Duration expiresIn);

    /** Only valid if the bucket is configured as public. */
    String publicUrl(String storageKey);
}
