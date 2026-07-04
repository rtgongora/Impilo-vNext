package zw.gov.mohcc.impilo.docstore.core.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO/S3-compatible provider implementation for document binary storage.
 */
@Component
public class MinioObjectStorageProvider implements ObjectStorageProvider {

    private final MinioClient minioClient;

    public MinioObjectStorageProvider(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public String providerType() {
        return "MINIO";
    }

    @Override
    public void ensureBucketExists(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to ensure bucket exists: " + e.getMessage(), e);
        }
    }

    @Override
    public String putObject(String bucket, String objectKey, byte[] content, String mimeType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(mimeType)
                    .build());
            return objectKey;
        } catch (Exception e) {
            throw new RuntimeException("Failed to put object: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream getObject(String bucket, String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get object: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateSignedUrl(String bucket, String objectKey, int ttlSeconds) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(ttlSeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signed URL: " + e.getMessage(), e);
        }
    }

    @Override
    public ObjectStat statObject(String bucket, String objectKey) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return new ObjectStat(stat.size(), stat.contentType());
        } catch (ErrorResponseException e) {
            String code = e.errorResponse() != null ? e.errorResponse().code() : null;
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)
                    || "NoSuchBucket".equals(code) || "ResourceNotFound".equals(code)) {
                return null;
            }
            throw new RuntimeException("Failed to stat object: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to stat object: " + e.getMessage(), e);
        }
    }

    @Override
    public void removeObject(String bucket, String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to remove object: " + e.getMessage(), e);
        }
    }
}
