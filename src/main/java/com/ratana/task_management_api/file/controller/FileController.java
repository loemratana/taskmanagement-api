package com.ratana.task_management_api.file.controller;


import com.ratana.task_management_api.common.response.ApiResponse;
import com.ratana.task_management_api.file.dto.response.FileUploadResponse;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.infrastructure.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Generic file storage, scoped per caller. Task attachments and work submissions have their own
 * endpoints with task-level authorization; this controller must never reach their objects.
 *
 * <p>Namespaces:
 * <ul>
 *   <li>{@code users/{callerId}/...} — the caller's own files (default for every upload)</li>
 *   <li>{@code announcements/...} — announcement banners/attachments; write needs an announcements
 *       permission, read is open to any signed-in user (announcements are company-wide content)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private static final String USERS_PREFIX = "users/";
    private static final String ANNOUNCEMENTS_PREFIX = "announcements";
    private static final String ANNOUNCEMENTS_KEY_PREFIX = ANNOUNCEMENTS_PREFIX + "/";

    private final StorageService storageService;


    /** POST /api/v1/files/upload?directory=reports/2026 (form-data: file) → users/{me}/reports/2026/... */
    @PostMapping(value ="/upload",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(@RequestPart("file")MultipartFile file,
                                                                  @RequestParam(value = "directory",required = false) String directory,
                                                                  @AuthenticationPrincipal UserPrincipal principal)
    {
        String scoped = scopedDirectory(directory, principal);
        return  ResponseEntity.ok(ApiResponse.success("File uploaded", storageService.upload(file, scoped)));
    }

    /** Upload several files into the same (scoped) directory. */
    @PostMapping(value = "/upload-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<FileUploadResponse>>> uploadMultiple(@RequestPart("file")List<MultipartFile> files,
                                                                                @RequestParam(value = "directory", required = false) String directory,
                                                                                @AuthenticationPrincipal UserPrincipal principal)
    {
        String scoped = scopedDirectory(directory, principal);
        List<FileUploadResponse> result = files.stream().map(f ->storageService.upload(f, scoped)).toList();
        return ResponseEntity.ok(ApiResponse.success("Files uploaded", result));
    }

    /** GET /api/v1/files/url?key=users/{me}/reports/xxx-photo.png */
    @GetMapping("/url")
    public ResponseEntity<ApiResponse<String>> signedUrl(@RequestParam("key") String key,
                                                         @AuthenticationPrincipal UserPrincipal principal) {
        String safeKey = assertSafeKey(key);
        if (!isOwnKey(safeKey, principal) && !safeKey.startsWith(ANNOUNCEMENTS_KEY_PREFIX)) {
            throw denied("read", safeKey, principal);
        }
        return ResponseEntity.ok(ApiResponse.success("Signed URL created", storageService.createSignedUrl(safeKey, Duration.ofHours(1))));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> delete(@RequestParam("key") String key,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        String safeKey = assertSafeKey(key);
        boolean allowed = isOwnKey(safeKey, principal)
                || (safeKey.startsWith(ANNOUNCEMENTS_KEY_PREFIX) && canManageAnnouncementFiles(principal));
        if (!allowed) {
            throw denied("delete", safeKey, principal);
        }
        storageService.delete(safeKey);
        return ResponseEntity.ok(ApiResponse.success("File deleted"));
    }

    /**
     * Announcement admins may write under announcements/; everything else is forced under the
     * caller's own users/{id}/ prefix. Segment syntax is still validated by the storage service.
     */
    private String scopedDirectory(String requested, UserPrincipal principal) {
        String dir = StringUtils.hasText(requested)
                ? requested.trim().replace('\\', '/').replaceAll("^/+|/+$", "").toLowerCase(Locale.ROOT)
                : "";
        if ((dir.equals(ANNOUNCEMENTS_PREFIX) || dir.startsWith(ANNOUNCEMENTS_KEY_PREFIX))
                && canManageAnnouncementFiles(principal)) {
            return dir;
        }
        String own = USERS_PREFIX + principal.getId();
        return dir.isEmpty() ? own : own + "/" + dir;
    }

    private static boolean isOwnKey(String key, UserPrincipal principal) {
        return key.startsWith(USERS_PREFIX + principal.getId() + "/");
    }

    private static boolean canManageAnnouncementFiles(UserPrincipal principal) {
        return principal.hasAnyPermission("announcements:create", "announcements:update");
    }

    /** Object keys are literal, but reject traversal-looking input outright rather than reason about it. */
    private static String assertSafeKey(String key) {
        if (!StringUtils.hasText(key) || key.startsWith("/") || key.contains("..") || key.contains("\\")) {
            throw new AccessDeniedException("Invalid file key.");
        }
        return key.trim();
    }

    private static AccessDeniedException denied(String action, String key, UserPrincipal principal) {
        log.warn("File {} denied: user {} key {}", action, principal.getId(), key);
        return new AccessDeniedException("You do not have access to this file.");
    }
}
