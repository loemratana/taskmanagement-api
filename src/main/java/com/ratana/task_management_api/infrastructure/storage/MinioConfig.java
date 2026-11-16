package com.ratana.task_management_api.infrastructure.storage;


import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class MinioConfig {



    @Bean
    @Primary
    public MinioClient minioClient(MinioProperties properties)
    {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .region(properties.region())
                .build();
    }

    /**
     * Used ONLY to sign download URLs. The host is part of the signature, so the URL must be
     * signed with the host the browser will call (localhost:9100), not the internal Docker host.
     * Setting region avoids a network call, so this client never needs to reach that host.
     */
    @Bean
    public MinioClient minioPresignClient(MinioProperties properties)
    {
        return MinioClient.builder()
                .endpoint(properties.publicEndpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .region(properties.region())
                .build();
    }

    /** Creates the bucket on startup if it does not exist yet. */
    @Bean
    public ApplicationRunner minioBucketInitializer(MinioClient minioClient,MinioProperties properties)
    {
        return  args -> {
            String bucket = properties.bucket();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists)
            {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket '{}'", bucket);
            }
            // Avatars and room photos are shown in lists everywhere, so they get permanent public
            // URLs instead of expiring signed ones. Everything else in the bucket stays private.
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(publicReadPolicy(bucket, "avatars/*", "rooms/*"))
                    .build());
        };
    }

    private static String publicReadPolicy(String bucket, String... prefixes)
    {
        String resources = Arrays.stream(prefixes)
                .map(prefix -> "\"arn:aws:s3:::%s/%s\"".formatted(bucket, prefix))
                .collect(Collectors.joining(", "));
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"AWS": ["*"]},
                    "Action": ["s3:GetObject"],
                    "Resource": [%s]
                  }]
                }
                """.formatted(resources);
    }
}
