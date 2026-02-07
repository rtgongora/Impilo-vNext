package zw.gov.mohcc.impilo.landela.core;

import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import zw.gov.mohcc.impilo.landela.config.LandelaProperties;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * Dual-mode storage router.
 *
 * Routes document storage operations to either:
 *   INTERNAL — MinIO object storage
 *   LANDELA  — External Landela DMS REST API
 *
 * The active mode is controlled by landela.mode in application.yml.
 */
@Service
public class StorageRouter {

    private static final Logger log = LoggerFactory.getLogger(StorageRouter.class);

    private final LandelaProperties properties;
    private final MinioClient minioClient;
    private final RestTemplate restTemplate;

    public StorageRouter(LandelaProperties properties, MinioClient minioClient) {
        this.properties = properties;
        this.minioClient = minioClient;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Store a file and return the storage reference (object key or Landela doc ID).
     */
    public String store(String objectKey, MultipartFile file, String mimeType) {
        if (isLandelaMode()) {
            return storeLandela(objectKey, file, mimeType);
        }
        return storeInternal(objectKey, file, mimeType);
    }

    /**
     * Generate a pre-signed GET URL for a stored document.
     */
    public String generateSignedUrl(String storageRef) {
        if (isLandelaMode()) {
            return generateSignedUrlLandela(storageRef);
        }
        return generateSignedUrlInternal(storageRef);
    }

    /**
     * Get an input stream to the stored document for proxy download.
     */
    public InputStream getObject(String storageRef) {
        if (isLandelaMode()) {
            return getObjectLandela(storageRef);
        }
        return getObjectInternal(storageRef);
    }

    /**
     * Remove a stored document.
     */
    public void remove(String storageRef) {
        if (isLandelaMode()) {
            removeLandela(storageRef);
        } else {
            removeInternal(storageRef);
        }
    }

    /**
     * Returns the active storage provider name.
     */
    public String getProviderName() {
        return isLandelaMode() ? "LANDELA" : "INTERNAL";
    }

    // ---- Internal (MinIO) adapter ----

    private String storeInternal(String objectKey, MultipartFile file, String mimeType) {
        try {
            String bucket = properties.getInternal().getMinioBucket();
            ensureBucketExists(bucket);

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(mimeType)
                            .build());

            log.info("Stored object in MinIO: bucket={}, key={}", bucket, objectKey);
            return objectKey;
        } catch (Exception e) {
            throw new StorageException("Failed to store object in MinIO: " + e.getMessage(), e);
        }
    }

    private String generateSignedUrlInternal(String storageRef) {
        try {
            String bucket = properties.getInternal().getMinioBucket();
            int ttl = properties.getSignedUrl().getTtlSeconds();

            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(storageRef)
                            .expiry(ttl, TimeUnit.SECONDS)
                            .build());

            log.debug("Generated signed URL for MinIO object: key={}", storageRef);
            return url;
        } catch (Exception e) {
            throw new StorageException("Failed to generate signed URL from MinIO: " + e.getMessage(), e);
        }
    }

    private InputStream getObjectInternal(String storageRef) {
        try {
            String bucket = properties.getInternal().getMinioBucket();
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(storageRef)
                            .build());
        } catch (Exception e) {
            throw new StorageException("Failed to get object from MinIO: " + e.getMessage(), e);
        }
    }

    private void removeInternal(String storageRef) {
        try {
            String bucket = properties.getInternal().getMinioBucket();
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(storageRef)
                            .build());
            log.info("Removed object from MinIO: bucket={}, key={}", bucket, storageRef);
        } catch (Exception e) {
            throw new StorageException("Failed to remove object from MinIO: " + e.getMessage(), e);
        }
    }

    private void ensureBucketExists(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            throw new StorageException("Failed to ensure MinIO bucket exists: " + e.getMessage(), e);
        }
    }

    // ---- Landela DMS adapter ----

    private String storeLandela(String objectKey, MultipartFile file, String mimeType) {
        try {
            String url = properties.getLandela().getBaseUrl() + "/api/v1/documents";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("X-API-Key", properties.getLandela().getApiKey());

            org.springframework.util.LinkedMultiValueMap<String, Object> body =
                    new org.springframework.util.LinkedMultiValueMap<>();
            body.add("file", file.getResource());
            body.add("objectKey", objectKey);

            HttpEntity<org.springframework.util.LinkedMultiValueMap<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<LandelaUploadResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, LandelaUploadResponse.class);

            if (response.getBody() != null && response.getBody().documentId() != null) {
                log.info("Stored document in Landela DMS: externalId={}", response.getBody().documentId());
                return response.getBody().documentId();
            }

            throw new StorageException("Landela DMS returned empty response");
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Failed to store document in Landela DMS: " + e.getMessage(), e);
        }
    }

    private String generateSignedUrlLandela(String storageRef) {
        try {
            String url = properties.getLandela().getBaseUrl()
                    + "/api/v1/documents/" + storageRef + "/signed-url"
                    + "?ttl=" + properties.getSignedUrl().getTtlSeconds();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-Key", properties.getLandela().getApiKey());

            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<LandelaSignedUrlResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, LandelaSignedUrlResponse.class);

            if (response.getBody() != null && response.getBody().url() != null) {
                return response.getBody().url();
            }

            throw new StorageException("Landela DMS returned empty signed URL response");
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Failed to generate signed URL from Landela DMS: " + e.getMessage(), e);
        }
    }

    private InputStream getObjectLandela(String storageRef) {
        try {
            String url = properties.getLandela().getBaseUrl()
                    + "/api/v1/documents/" + storageRef + "/content";

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-Key", properties.getLandela().getApiKey());

            HttpEntity<Void> request = new HttpEntity<>(headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, byte[].class);

            if (response.getBody() != null) {
                return new java.io.ByteArrayInputStream(response.getBody());
            }

            throw new StorageException("Landela DMS returned empty content");
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Failed to get document from Landela DMS: " + e.getMessage(), e);
        }
    }

    private void removeLandela(String storageRef) {
        try {
            String url = properties.getLandela().getBaseUrl()
                    + "/api/v1/documents/" + storageRef;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-API-Key", properties.getLandela().getApiKey());

            HttpEntity<Void> request = new HttpEntity<>(headers);
            restTemplate.exchange(url, HttpMethod.DELETE, request, Void.class);

            log.info("Removed document from Landela DMS: externalId={}", storageRef);
        } catch (Exception e) {
            throw new StorageException("Failed to remove document from Landela DMS: " + e.getMessage(), e);
        }
    }

    private boolean isLandelaMode() {
        return "LANDELA".equalsIgnoreCase(properties.getMode());
    }

    // ---- Landela response DTOs ----

    private record LandelaUploadResponse(String documentId) {}
    private record LandelaSignedUrlResponse(String url) {}
}
