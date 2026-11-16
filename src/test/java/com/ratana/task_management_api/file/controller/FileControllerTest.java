package com.ratana.task_management_api.file.controller;

import com.ratana.task_management_api.file.dto.response.FileUploadResponse;
import com.ratana.task_management_api.infrastructure.security.UserPrincipal;
import com.ratana.task_management_api.infrastructure.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** DEF-05: /files must stay inside the caller's namespace (TC-SEC-10..13). */
class FileControllerTest {

    private StorageService storageService;
    private FileController controller;
    private UserPrincipal employee;
    private UserPrincipal announcementAdmin;
    private final MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});

    private static UserPrincipal principal(String... permissions) {
        return new UserPrincipal(UUID.randomUUID(), "u@example.com", "hash", null,
                java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList(), true);
    }

    @BeforeEach
    void setUp() {
        storageService = mock(StorageService.class);
        when(storageService.upload(any(), any())).thenReturn(FileUploadResponse.builder().build());
        controller = new FileController(storageService);
        employee = principal("tasks:read");
        announcementAdmin = principal("announcements:create");
    }

    @Test
    @DisplayName("upload without directory goes to users/{me}")
    void upload_defaultsToOwnNamespace() {
        controller.upload(file, null, employee);
        verify(storageService).upload(file, "users/" + employee.getId());
    }

    @Test
    @DisplayName("TC-SEC-12/13: requested task or avatars directory is nested under users/{me}")
    void upload_cannotEscapeOwnNamespace() {
        UUID taskId = UUID.randomUUID();
        controller.upload(file, "tasks/" + taskId + "/attachments", employee);
        controller.upload(file, "avatars", employee);

        verify(storageService).upload(file, "users/" + employee.getId() + "/tasks/" + taskId + "/attachments");
        verify(storageService).upload(file, "users/" + employee.getId() + "/avatars");
    }

    @Test
    @DisplayName("announcement admins may upload under announcements/, others may not")
    void upload_announcementsRequiresPermission() {
        controller.uploadMultiple(List.of(file), "announcements/banners", announcementAdmin);
        controller.upload(file, "announcements/banners", employee);

        verify(storageService).upload(file, "announcements/banners");
        verify(storageService).upload(file, "users/" + employee.getId() + "/announcements/banners");
    }

    @Test
    @DisplayName("TC-SEC-10: signing another user's or a task's key is denied")
    void signedUrl_foreignKeyDenied() {
        assertThatThrownBy(() -> controller.signedUrl("tasks/" + UUID.randomUUID() + "/submissions/x/files/a.pdf", employee))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.signedUrl("users/" + UUID.randomUUID() + "/a.png", employee))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.signedUrl("users/" + employee.getId() + "/../x", employee))
                .isInstanceOf(AccessDeniedException.class);
        verify(storageService, never()).createSignedUrl(any(), any());
    }

    @Test
    @DisplayName("own and announcement keys can be signed")
    void signedUrl_ownAndAnnouncementAllowed() {
        controller.signedUrl("users/" + employee.getId() + "/a.png", employee);
        controller.signedUrl("announcements/banners/b.png", employee);

        verify(storageService).createSignedUrl(eq("users/" + employee.getId() + "/a.png"), any(Duration.class));
        verify(storageService).createSignedUrl(eq("announcements/banners/b.png"), any(Duration.class));
    }

    @Test
    @DisplayName("TC-SEC-11: deleting a key outside the caller's namespace is denied")
    void delete_foreignKeyDenied() {
        assertThatThrownBy(() -> controller.delete("tasks/" + UUID.randomUUID() + "/attachments/a.pdf", employee))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.delete("announcements/banners/b.png", employee))
                .isInstanceOf(AccessDeniedException.class);
        verify(storageService, never()).delete(any());

        controller.delete("users/" + employee.getId() + "/a.png", employee);
        controller.delete("announcements/banners/b.png", announcementAdmin);
        verify(storageService, times(2)).delete(any());
    }
}
