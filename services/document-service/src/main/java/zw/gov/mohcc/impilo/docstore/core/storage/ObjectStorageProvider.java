package zw.gov.mohcc.impilo.docstore.core.storage;

import java.io.InputStream;

/**
 * Provider-neutral abstraction for document binary storage engines.
 */
public interface ObjectStorageProvider {

    String providerType();

    void ensureBucketExists(String bucket);

    String putObject(String bucket, String objectKey, byte[] content, String mimeType);

    InputStream getObject(String bucket, String objectKey);

    String generateSignedUrl(String bucket, String objectKey, int ttlSeconds);

    void removeObject(String bucket, String objectKey);

    /**
     * Metadata for an object that already exists in the backing store.
     * A negative {@code sizeBytes} means the provider could not determine the size.
     */
    record ObjectStat(long sizeBytes, String contentType) {
    }

    /**
     * Stat an existing object without downloading it. Returns {@code null} when the
     * object (or bucket) does not exist. Providers that cannot inspect external
     * storage locations (adapter-backed providers) fail closed rather than fabricate.
     */
    default ObjectStat statObject(String bucket, String objectKey) {
        throw new UnsupportedOperationException(
                providerType() + " storage provider does not support statObject — external registration requires direct object storage");
    }
}
