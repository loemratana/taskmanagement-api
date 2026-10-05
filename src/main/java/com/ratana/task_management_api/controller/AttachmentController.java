package com.ratana.task_management_api.controller;

import com.ratana.task_management_api.dto.response.ApiResponse;
import com.ratana.task_management_api.dto.response.AttachmentResponse;
import com.ratana.task_management_api.dto.response.PageResponse;
import com.ratana.task_management_api.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attachments")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping(value = "/tasks/{taskId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload an attachment to a task",
            description = "Accepts photos (any image/* type) and PDFs, plus a small set of office/zip types, up to 20MB.")
    public ResponseEntity<ApiResponse<AttachmentResponse>> upload(
            @PathVariable UUID taskId,
            @RequestParam("file") MultipartFile file) {
        AttachmentResponse response = attachmentService.upload(taskId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping(value = "/tasks/{taskId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List a task's attachments")
    public ResponseEntity<ApiResponse<PageResponse<AttachmentResponse>>> listForTask(
            @PathVariable UUID taskId,
            @ParameterObject Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(attachmentService.listForTask(taskId, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Download an attachment",
            description = "Redirects to a short-lived signed URL for the underlying file.")
    public ResponseEntity<Void> download(@PathVariable UUID id) {
        String signedUrl = attachmentService.download(id);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(signedUrl)).build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an attachment")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        attachmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
