package com.ratana.task_management_api.file.dto.response;


import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class FileUploadResponse {

    private String storageKey;  // e.g. "avatars/2026/3f2a...-photo.png" — save this in the DB
    private String directory;
    private String fileName;
    private String originalFileName;
    private String contentType;
    private long size;
    private String url;             // temporary signed download URL
}
