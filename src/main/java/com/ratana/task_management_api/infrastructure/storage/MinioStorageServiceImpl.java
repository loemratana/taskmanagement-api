package com.ratana.task_management_api.infrastructure.storage;

import com.ratana.task_management_api.file.config.AttachmentUploadPolicy;
import com.ratana.task_management_api.file.dto.response.FileUploadResponse;
import com.ratana.task_management_api.file.exception.FileSizeExceededException;
import com.ratana.task_management_api.common.exception.InvalidRequestException;
import com.ratana.task_management_api.file.exception.UnsupportedFileTypeException;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Slf4j
@Service
public class MinioStorageServiceImpl implements StorageService {

    private static final Pattern SEGMENT_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,62}$");
    private static final int MAX_DIRECTORY_DEPTH = 5;
    private static final String DEFAULT_DIRECTORY = "uploads";
    private static final Duration DEFAULT_URL_EXPIRY = Duration.ofHours(1);


    private final MinioClient minioClient;
    private final MinioClient minioPresignClient;
    private final MinioProperties minioProperties;

    // Explicit constructor: Lombok does not copy @Qualifier onto generated constructor params.
    public MinioStorageServiceImpl(MinioClient minioClient,
                                   @Qualifier("minioPresignClient") MinioClient minioPresignClient,
                                   MinioProperties minioProperties) {
        this.minioClient = minioClient;
        this.minioPresignClient = minioPresignClient;
        this.minioProperties = minioProperties;
    }

    @Override
    public FileUploadResponse upload(MultipartFile file, String directory) {
        validateFile(file);
        String dir = normalizeDirectory(directory);
        String originalName = file.getOriginalFilename();
        String fileName = UUID.randomUUID() +"_" +sanitizeFileName(originalName);
        String storageKey = dir + "/" + fileName;
        String contentType = resolveContentType(file.getContentType());
        log.info("Uploading file. bucket={}, key={}, size={}", minioProperties.bucket(), storageKey, file.getSize());

        try(InputStream in = file.getInputStream()) {

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.bucket())
                    .object(storageKey)
                    .stream(in, file.getSize(), -1)
                    .contentType(contentType)
                    .build());


        }
        catch (Exception e)
        {
            log.error("Failed to upload file. bucket={}, key={}", minioProperties.bucket(), storageKey, e);
            throw  new StorageException("Failed to upload file to storage.", e);
        }
        return FileUploadResponse.builder()
                .storageKey(storageKey)
                .directory(dir)
                .fileName(fileName)
                .originalFileName(originalName)
                .contentType(contentType)
                .size(file.getSize())
                .url(createSignedUrl(storageKey,DEFAULT_URL_EXPIRY))
                .build();
    }

    @Override
    public void upload(String storageKey, byte[] content, String contentType) {
        log.info("Uploading file. bucket={}, key={}", minioProperties.bucket(), storageKey);
        try (InputStream in = new ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.bucket())
                    .object(storageKey)
                    .stream(in, content.length, -1)
                    .contentType(resolveContentType(contentType))
                    .build());
        } catch (Exception e) {
            log.error("Failed to upload file. bucket={}, key={}", minioProperties.bucket(), storageKey, e);
            throw new StorageException("Failed to upload file to storage.", e);
        }
    }

    @Override
    public void delete(String storageKey) {
        log.info("Deleting file. bucket={}, key={}", minioProperties.bucket(), storageKey);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.bucket())
                    .object(storageKey)
                    .build());
        } catch (Exception e) {
            log.error("Failed to delete file. bucket={}, key={}", minioProperties.bucket(), storageKey, e);
            throw new StorageException("Failed to delete file from storage.", e);
        }

    }

    @Override
    public byte[] download(String storageKey) {
        log.debug("Downloading file. bucket={}, key={}", minioProperties.bucket(), storageKey);
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioProperties.bucket())
                .object(storageKey)
                .build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            log.error("Failed to download file. bucket={}, key={}", minioProperties.bucket(), storageKey, e);
            throw new StorageException("Failed to download file from storage.", e);
        }
    }

    @Override
    public String createSignedUrl(String storageKey, Duration expiresIn) {

        try {
            return minioPresignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(minioProperties.bucket())
                    .object(storageKey)
                    .expiry((int) expiresIn.toSeconds())   // max 7 days
                    .build());

        } catch (Exception e) {
            log.error("Failed to create signed URL. bucket={}, key={}", minioProperties.bucket(), storageKey, e);
            throw new StorageException("Failed to generate a download link.", e);
        }
    }

    @Override
    public String publicUrl(String storageKey) {
        return minioProperties.publicEndpoint() + "/" + minioProperties.bucket() + "/" + storageKey;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Uploaded file is empty.");
        }

        if (file.getSize() > AttachmentUploadPolicy.MAX_FILE_SIZE_BYTES) {
            throw new FileSizeExceededException("File exceeds the 20MB limit.");
        }

        if (!AttachmentUploadPolicy.isAllowed(file)) {
            throw new UnsupportedFileTypeException("File type not allowed: " + file.getContentType());
        }


    }

    private String normalizeDirectory(String directory)
    {
        if (directory == null || directory.isBlank()) {
            return DEFAULT_DIRECTORY;
        }
        String[] segments = Arrays.stream(directory.trim().replace('\\', '/').split("/"))
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        if (segments.length == 0) {
            return DEFAULT_DIRECTORY;
        }
        if (segments.length > MAX_DIRECTORY_DEPTH) {
            throw new InvalidDirectoryException("Directory can have at most " + MAX_DIRECTORY_DEPTH + " levels.");
        }
        for (String segment : segments) {
            if (!SEGMENT_PATTERN.matcher(segment).matches()) {
                throw new InvalidDirectoryException(
                        "Invalid directory name '" + segment + "'. Use only a-z, 0-9, '-' or '_'.");
            }
        }
        return Arrays.stream(segments).collect(Collectors.joining("/"));
    }

    private String sanitizeFileName(String name) {

        if (name == null || name.isBlank())
        {
            return "file";
        }
        String base = name.substring(Math.max(name.lastIndexOf('/'),name.lastIndexOf('\\')) +1);
        String safe = base.replaceAll("[^A-Za-z0-9._-]", "_");

        return  safe.length() >100 ? safe.substring(safe.length() -100) :safe;

    }

    private String resolveContentType(String contentType) {
        return contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

}
