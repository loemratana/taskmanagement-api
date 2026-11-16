package com.ratana.task_management_api.file.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.io.TikaInputStream;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * Which uploads are accepted. The client's declared Content-Type is not trusted on its own:
 * the file's actual bytes are sniffed (Apache Tika) and must also be an allowed type, so an
 * HTML page renamed to photo.png is rejected.
 */
@Slf4j
public final class AttachmentUploadPolicy {

    private AttachmentUploadPolicy() {
    }

    public static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024; // 20MB

    public static final Set<String> ALLOWED_EXACT_MIME_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/zip",
            "text/plain"
    );

    /** SVG is an image/* type but can carry scripts, and avatars are publicly readable — never accept it. */
    private static final String SVG = "image/svg+xml";

    /** Thread-safe; detection reads only the first few KB of the stream. */
    private static final Tika TIKA = new Tika();

    /** image/* (except SVG) is allowed via prefix match; everything else must be an exact allow-listed type. */
    public static boolean isAllowed(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return false;
        }
        if (isImage(mimeType)) {
            return true;
        }
        return ALLOWED_EXACT_MIME_TYPES.contains(mimeType);
    }

    public static boolean isImage(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/") && !SVG.equalsIgnoreCase(mimeType);
    }

    /** Both the declared type and the type detected from the file's content are allowed. */
    public static boolean isAllowed(MultipartFile file) {
        if (!isAllowed(file.getContentType())) {
            return false;
        }
        String detected = detectContentType(file);
        if (!isAllowed(detected)) {
            log.warn("Upload rejected: '{}' declared {} but its content is {}",
                    file.getOriginalFilename(), file.getContentType(), detected);
            return false;
        }
        return true;
    }

    /** Both the declared type and the type detected from the file's content are images (not SVG). */
    public static boolean isImage(MultipartFile file) {
        if (!isImage(file.getContentType())) {
            return false;
        }
        String detected = detectContentType(file);
        if (!isImage(detected)) {
            log.warn("Image upload rejected: '{}' declared {} but its content is {}",
                    file.getOriginalFilename(), file.getContentType(), detected);
            return false;
        }
        return true;
    }

    /**
     * The type identified from the file's bytes (magic numbers), refined by the file name for
     * container formats (e.g. a .docx is a zip). Unreadable content yields application/octet-stream,
     * which no rule allows.
     */
    public static String detectContentType(MultipartFile file) {
        try (InputStream in = TikaInputStream.get(file.getInputStream())) {
            return TIKA.detect(in, file.getOriginalFilename());
        } catch (IOException e) {
            log.warn("Could not read upload '{}' to detect its type", file.getOriginalFilename(), e);
            return "application/octet-stream";
        }
    }
}
