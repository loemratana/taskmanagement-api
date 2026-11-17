package com.ratana.task_management_api.infrastructure.storage;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

/**
 * Reports MinIO as UP only when the server is reachable and the app's bucket exists.
 * Exposed as the "minio" component of /management/health and included in the readiness group.
 */
@Component("minio")
@RequiredArgsConstructor
public class MinioHealthIndicator extends AbstractHealthIndicator
{
    private final MinioClient minioClient;
    private final MinioProperties properties;

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception
    {
        String bucket = properties.bucket();
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        builder.withDetail("bucket", bucket);
        if (exists)
        {
            builder.up();
        }
        else
        {
            builder.down().withDetail("error", "bucket does not exist");
        }
    }
}
