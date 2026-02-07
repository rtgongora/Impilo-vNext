package zw.gov.mohcc.impilo.varapi.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;

import java.util.UUID;

/**
 * MinIO/S3-compatible object storage client for provider document uploads
 * and downloads (e.g., scanned certificates, council registration letters).
 *
 * <p>In production, this class should be backed by the MinIO Java SDK for
 * real object storage operations. The current implementation provides a
 * lightweight reference-based stub suitable for local development and
 * integration testing, deferring the actual MinIO SDK dependency to a
 * later infrastructure phase.</p>
 *
 * <p>Storage references follow the pattern {@code {bucket}/{key}} and are
 * persisted in the {@code documents} table via {@code storage_ref}.</p>
 */
@Service
public class DocumentStorageClient {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageClient.class);

    private final VarapiProperties varapiProperties;

    public DocumentStorageClient(VarapiProperties varapiProperties) {
        this.varapiProperties = varapiProperties;
    }

    /**
     * Upload a document to object storage.
     *
     * <p>In the current stub implementation the document bytes are not persisted;
     * only the storage reference is generated and returned. A production
     * implementation should use the MinIO Java SDK to perform the actual PUT.</p>
     *
     * @param key         the object key (typically {@code {providerId}/{documentType}/{uuid}})
     * @param content     the raw document bytes
     * @param contentType the MIME type (e.g., "application/pdf", "image/jpeg")
     * @return the storage reference in the form {@code {bucket}/{key}}
     * @throws IllegalArgumentException if key or content is null/empty
     */
    public String uploadDocument(String key, byte[] content, String contentType) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Document key must not be null or blank");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Document content must not be null or empty");
        }

        String bucket = varapiProperties.getDocument().getMinioBucket();
        String storageRef = bucket + "/" + key;

        log.info("Document uploaded to storage: ref={}, size={} bytes, contentType={}",
                storageRef, content.length, contentType);

        return storageRef;
    }

    /**
     * Generate a unique object key for a provider document.
     *
     * @param providerId   the provider's internal ID
     * @param documentType the document type (e.g., "certificate", "id-scan")
     * @return a unique key in the form {@code {providerId}/{documentType}/{uuid}}
     */
    public String generateKey(Long providerId, String documentType) {
        return providerId + "/" + documentType + "/" + UUID.randomUUID();
    }

    /**
     * Download a document from object storage.
     *
     * <p>Not yet implemented — requires the MinIO Java SDK for production use.
     * Throws {@link UnsupportedOperationException} until the SDK is integrated.</p>
     *
     * @param storageRef the storage reference returned by {@link #uploadDocument}
     * @return the raw document bytes
     * @throws UnsupportedOperationException always, until MinIO SDK integration
     */
    public byte[] downloadDocument(String storageRef) {
        if (storageRef == null || storageRef.isBlank()) {
            throw new IllegalArgumentException("Storage reference must not be null or blank");
        }
        log.info("Document download requested: ref={}", storageRef);
        throw new UnsupportedOperationException(
                "MinIO SDK integration required for production downloads");
    }

    /**
     * Delete a document from object storage.
     *
     * <p>In the current stub implementation this is a no-op that only logs the
     * deletion request. A production implementation should use the MinIO Java
     * SDK to perform the actual DELETE.</p>
     *
     * @param storageRef the storage reference returned by {@link #uploadDocument}
     */
    public void deleteDocument(String storageRef) {
        if (storageRef == null || storageRef.isBlank()) {
            log.warn("Attempted to delete document with null/blank storage reference");
            return;
        }
        log.info("Document deletion requested: ref={}", storageRef);
    }

    /**
     * Check whether a document exists in object storage.
     *
     * <p>Stub implementation that always returns {@code true} for non-blank
     * references. A production implementation should issue a HEAD request
     * against MinIO.</p>
     *
     * @param storageRef the storage reference to check
     * @return {@code true} if the reference is non-blank (stub behaviour)
     */
    public boolean documentExists(String storageRef) {
        if (storageRef == null || storageRef.isBlank()) {
            return false;
        }
        log.debug("Document existence check: ref={}", storageRef);
        return true;
    }
}
