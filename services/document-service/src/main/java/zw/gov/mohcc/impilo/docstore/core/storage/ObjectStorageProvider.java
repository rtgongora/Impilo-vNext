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
}
