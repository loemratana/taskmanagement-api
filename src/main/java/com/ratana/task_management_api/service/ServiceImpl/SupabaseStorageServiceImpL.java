package com.ratana.task_management_api.service.ServiceImpl;

import com.ratana.task_management_api.config.SupabaseProperties;
import com.ratana.task_management_api.dto.request.SignedUrlRequest;
import com.ratana.task_management_api.dto.response.SignedUrlResponse;
import com.ratana.task_management_api.exception.SupabaseStorageException;
import com.ratana.task_management_api.service.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;


@Service
@Slf4j
@RequiredArgsConstructor
public class SupabaseStorageServiceImpL implements SupabaseStorageService {

    private final SupabaseProperties properties;
    private final RestClient supabaseRestClient;

    private static final String OBJECT_PATH_TEMPLATE = "/storage/v1/object/{bucket}/{key}";
    private static final String SIGN_PATH_TEMPLATE = "/storage/v1/object/sign/{bucket}/{key}";
    private static final String PUBLIC_OBJECT_PATH_TEMPLATE = "/storage/v1/object/public/{bucket}/{key}";
    private static final String STORAGE_API_PREFIX = "/storage/v1";

    private static final String HEADER_UPSERT = "x-upsert";
    private static final String UPSERT_DISABLED = "false";


    @Override
    public void upload(String storageKey, byte[] content, String contentType) {
        log.info("Uploading file. bucket={}, key={}", properties.bucket(), storageKey);

        try {
            supabaseRestClient.post()
                    .uri(OBJECT_PATH_TEMPLATE, properties.bucket(), storageKey)
                    .header("Content-Type", resolveContentType(contentType))
                    .header(HEADER_UPSERT, UPSERT_DISABLED)
                    .body(content)
                    .retrieve()
                    .toBodilessEntity();

        } catch (Exception e) {
            log.error("Failed to upload file. bucket={}, key={}", properties.bucket(), storageKey);
        }


    }

    @Override
    public void delete(String storageKey) {
        log.info("Deleting file. bucket={}, key={}", properties.bucket(), storageKey);
        try
        {
            supabaseRestClient.method(HttpMethod.DELETE)
                    .uri(OBJECT_PATH_TEMPLATE, properties.bucket(), storageKey)
                    .retrieve()
                    .toBodilessEntity();
        }
        catch (Exception e)
        {
            log.error("Failed to delete file .bucket={}, key={}", properties.bucket(), storageKey,e);
            throw new SupabaseStorageException("Failed to delete file from storage.", e);

        }


    }

    @Override
    public String createSignedUrl(String storageKey, Duration expiresIn) {
        log.info("Creating signed URL. bucket={}, key={}, expiresInSeconds={}",
                properties.bucket(), storageKey, expiresIn.toSeconds());
        try
        {

            SignedUrlResponse response = requestSignedUrl(storageKey, expiresIn);

            return  signedUrlUri(requiredSignedPath(response,storageKey));

        }
        catch (SupabaseStorageException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to create signed URL. bucket={}, key={}", properties.bucket(), storageKey, ex);
            throw new SupabaseStorageException("Failed to generate a download link.", ex);
        }
    }

    @Override
    public String publicUrl(String storageKey) {
        return objectUri(storageKey);
    }

    private SignedUrlResponse requestSignedUrl(String storageKey, Duration expiresIn) {
        return supabaseRestClient.post()
                .uri(SIGN_PATH_TEMPLATE, properties.bucket(), storageKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SignedUrlRequest(expiresIn.toSeconds()))
                .retrieve()
                .body(SignedUrlResponse.class);
    }

    private String requiredSignedPath(SignedUrlResponse response,String storageKey) {
        if (response ==null || response.signedURL()==null)
        {
            throw new SupabaseStorageException("Supabase returned an empty signed URL response for key=%s.".formatted(storageKey),null);
        }

        return  response.signedURL();
    }

    /** Resolves Supabase's relative signed path (e.g. "/object/sign/...&token=...") to an absolute URL. */
    private String signedUrlUri(String relativeSignedPath) {
        return properties.url() + STORAGE_API_PREFIX + relativeSignedPath;
    }


    /** Builds the absolute public object URL. Only meaningful if the bucket itself is public. */
    private String objectUri(String storageKey) {
        return properties.url() + PUBLIC_OBJECT_PATH_TEMPLATE
                .replace("{bucket}", properties.bucket())
                .replace("{key}", storageKey);
    }

    private String resolveContentType(String contentType) {
        return contentType !=null ?contentType: MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
