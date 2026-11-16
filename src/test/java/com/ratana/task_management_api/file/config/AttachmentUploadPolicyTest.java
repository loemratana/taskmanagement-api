package com.ratana.task_management_api.file.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentUploadPolicyTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0};
    private static final byte[] PDF = "%PDF-1.7\n%âã\n1 0 obj\n".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] HTML = "<!DOCTYPE html><html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SVG = "<?xml version=\"1.0\"?><svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
            .getBytes(StandardCharsets.UTF_8);

    private static MockMultipartFile file(String name, String declaredType, byte[] content) {
        return new MockMultipartFile("file", name, declaredType, content);
    }

    @Test
    @DisplayName("real images, PDFs and text pass")
    void genuineFiles_areAllowed() {
        assertThat(AttachmentUploadPolicy.isImage(file("a.png", "image/png", PNG))).isTrue();
        assertThat(AttachmentUploadPolicy.isImage(file("a.jpg", "image/jpeg", JPEG))).isTrue();
        assertThat(AttachmentUploadPolicy.isAllowed(file("a.pdf", "application/pdf", PDF))).isTrue();
        assertThat(AttachmentUploadPolicy.isAllowed(
                file("notes.txt", "text/plain", "meeting notes\nline two\n".getBytes(StandardCharsets.UTF_8)))).isTrue();
    }

    @Test
    @DisplayName("a .docx (zip container) is recognised as Word, not rejected as a bare zip")
    void docx_isAllowed() throws IOException {
        MockMultipartFile docx = file("report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", minimalOoxml());
        assertThat(AttachmentUploadPolicy.isAllowed(docx)).isTrue();
    }

    @Test
    @DisplayName("an HTML page renamed to .png is rejected even though the client says image/png")
    void disguisedHtml_isRejected() {
        MockMultipartFile fake = file("photo.png", "image/png", HTML);
        assertThat(AttachmentUploadPolicy.isImage(fake)).isFalse();
        assertThat(AttachmentUploadPolicy.isAllowed(fake)).isFalse();
    }

    @Test
    @DisplayName("SVG is never accepted, declared or detected")
    void svg_isRejected() {
        assertThat(AttachmentUploadPolicy.isAllowed("image/svg+xml")).isFalse();
        assertThat(AttachmentUploadPolicy.isImage(file("logo.svg", "image/svg+xml", SVG))).isFalse();
        // Declared as PNG, content is SVG.
        assertThat(AttachmentUploadPolicy.isImage(file("logo.png", "image/png", SVG))).isFalse();
    }

    @Test
    @DisplayName("a declared type outside the allow-list is rejected before sniffing")
    void disallowedDeclaredType_isRejected() {
        assertThat(AttachmentUploadPolicy.isAllowed(file("x.html", "text/html", HTML))).isFalse();
        assertThat(AttachmentUploadPolicy.isAllowed(file("x.png", null, PNG))).isFalse();
    }

    /** Smallest zip Tika's magic sees as OOXML: its first entry is [Content_Types].xml. */
    private static byte[] minimalOoxml() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>"
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<w:document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
