package com.ratana.task_management_api.file.controller;

import com.ratana.task_management_api.common.response.ApiResponse;
import com.ratana.task_management_api.file.dto.response.AttachmentResponse;
import com.ratana.task_management_api.common.pagination.PageResponse;
import com.ratana.task_management_api.file.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
@Tag(name = "Attachments", description = "Files attached to a task")
@SecurityRequirement(name = "bearerAuth")
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping(value = "/tasks/{taskId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Attach a file to a task",
            description = "Anyone who can view the task may attach files. Images, PDFs and common office/zip types, up to 20MB.")
    public ResponseEntity<ApiResponse<AttachmentResponse>> upload(
            @PathVariable UUID taskId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(attachmentService.upload(taskId, file)));
    }

    @GetMapping(value = "/tasks/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List a task's attachments",
            description = "Oldest first. Each downloadUrl is a signed link valid for 30 minutes.")
    public ResponseEntity<ApiResponse<PageResponse<AttachmentResponse>>> listForTask(
            @PathVariable UUID taskId,
            @ParameterObject @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(attachmentService.listForTask(taskId, pageable)));
    }

    @GetMapping(value = "/{id}/download-url", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a fresh download link", description = "Signed link valid for 30 minutes.")
    public ResponseEntity<ApiResponse<Map<String, String>>> downloadUrl(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("url", attachmentService.download(id))));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an attachment", description = "Allowed for the uploader or anyone who can update the task.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        attachmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
