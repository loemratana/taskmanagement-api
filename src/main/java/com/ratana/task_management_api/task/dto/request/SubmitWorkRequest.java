package com.ratana.task_management_api.task.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SubmitWorkRequest {

    @Size(max = 5000, message = "Note must be at most 5000 characters.")
    private String note;

    /** Required (checked in the service) when the task is past its due date; ignored otherwise. */
    @Size(max = 1000, message = "Late reason must be at most 1000 characters.")
    private String lateReason;

    @Size(max = 10, message = "You can upload at most 10 images.")
    private List<MultipartFile> images = new ArrayList<>();

    @Size(max = 10, message = "You can upload at most 10 files.")
    private List<MultipartFile> files = new ArrayList<>();

    /** Images without empty parts (some clients send an empty part when nothing is picked). */
    public List<MultipartFile> imageList() {
        return nonEmpty(images);
    }

    public List<MultipartFile> fileList() {
        return nonEmpty(files);
    }

    public String trimmedNote() {
        return note == null || note.isBlank() ? null : note.trim();
    }

    public String trimmedLateReason() {
        return lateReason == null || lateReason.isBlank() ? null : lateReason.trim();
    }

    @AssertTrue(message = "Submission must contain a note, an image or a file.")
    public boolean isNotEmpty() {
        return trimmedNote() != null || !imageList().isEmpty() || !fileList().isEmpty();
    }

    private static List<MultipartFile> nonEmpty(List<MultipartFile> parts) {
        if (parts == null) {
            return List.of();
        }
        return parts.stream().filter(p -> p != null && !p.isEmpty()).toList();
    }
}
