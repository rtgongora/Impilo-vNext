package zw.gov.mohcc.impilo.varapi.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.varapi.config.VarapiProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/**
 * MinIO/S3-compatible object storage client for provider document uploads
 * and downloads (e.g., scanned certificates, council registration letters).
 * <p>
 * Uses the S3-compatible REST API with AWS Signature V4 authentication,
 * which is supported by MinIO, AWS S3, and other S3-compatible storage.
 * <p>
 * Storage references follow the pattern {@code {bucket}/{key}} and are
 * persisted in the {@code documents} table via {@code storage_ref}.
 */
@Service
public class DocumentStorageClient {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageClient.class);
    private static final String REGION = "us-east-1";
    private static final String SERVICE = "s3";
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter ISO_DATETIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final VarapiProperties varapiProperties;

    public DocumentStorageClient(VarapiProperties varapiProperties) {
        this.varapiProperties = varapiProperties;
        ensureBucketExists();
    }

    /**
     * Upload a document to MinIO/S3 object storage.
     *
     * @param key         the object key (typically {@code {providerId}/{documentType}/{uuid}})
     * @param content     the raw document bytes
     * @param contentType the MIME type (e.g., "application/pdf", "image/jpeg")
     * @return the storage reference in the form {@code {bucket}/{key}}
     */
    public String uploadDocument(String key, byte[] content, String contentType) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Document key must not be null or blank");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Document content must not be null or empty");
        }

        VarapiProperties.DocumentProperties docProps = varapiProperties.getDocument();
        String bucket = docProps.getMinioBucket();
        String endpoint = docProps.getMinioEndpoint();

        try {
            String url = endpoint + "/" + bucket + "/" + key;
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("Content-Type", contentType);
            conn.setRequestProperty("Content-Length", String.valueOf(content.length));

            // AWS Signature V4 auth
            signRequest(conn, "PUT", bucket, key, content, docProps);

            conn.getOutputStream().write(content);
            conn.getOutputStream().flush();

            int status = conn.getResponseCode();
            if (status >= 200 && status < 300) {
                String storageRef = bucket + "/" + key;
                log.info("Document uploaded: ref={}, size={} bytes, contentType={}",
                        storageRef, content.length, contentType);
                return storageRef;
            } else {
                String error = readResponseBody(conn);
                log.error("Upload failed: key={}, status={}, error={}", key, status, error);
                throw new DocumentStorageException("Upload failed with HTTP " + status);
            }
        } catch (DocumentStorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Upload error: key={}, error={}", key, e.getMessage());
            throw new DocumentStorageException("Upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a unique object key for a provider document.
     */
    public String generateKey(Long providerId, String documentType) {
        return providerId + "/" + documentType + "/" + UUID.randomUUID();
    }

    /**
     * Download a document from MinIO/S3 object storage.
     *
     * @param storageRef the storage reference returned by {@link #uploadDocument}
     * @return the raw document bytes
     */
    public byte[] downloadDocument(String storageRef) {
        if (storageRef == null || storageRef.isBlank()) {
            throw new IllegalArgumentException("Storage reference must not be null or blank");
        }

        VarapiProperties.DocumentProperties docProps = varapiProperties.getDocument();
        String endpoint = docProps.getMinioEndpoint();

        try {
            String url = endpoint + "/" + storageRef;
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);

            int slashIdx = storageRef.indexOf('/');
            String bucket = storageRef.substring(0, slashIdx);
            String key = storageRef.substring(slashIdx + 1);
            signRequest(conn, "GET", bucket, key, new byte[0], docProps);

            int status = conn.getResponseCode();
            if (status == 200) {
                byte[] data = readStream(conn.getInputStream());
                log.info("Document downloaded: ref={}, size={} bytes", storageRef, data.length);
                return data;
            } else if (status == 404) {
                log.warn("Document not found: ref={}", storageRef);
                return null;
            } else {
                String error = readResponseBody(conn);
                log.error("Download failed: ref={}, status={}, error={}", storageRef, status, error);
                throw new DocumentStorageException("Download failed with HTTP " + status);
            }
        } catch (DocumentStorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Download error: ref={}, error={}", storageRef, e.getMessage());
            throw new DocumentStorageException("Download failed: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a document from MinIO/S3 object storage.
     */
    public void deleteDocument(String storageRef) {
        if (storageRef == null || storageRef.isBlank()) {
            log.warn("Attempted to delete document with null/blank storage reference");
            return;
        }

        VarapiProperties.DocumentProperties docProps = varapiProperties.getDocument();
        String endpoint = docProps.getMinioEndpoint();

        try {
            String url = endpoint + "/" + storageRef;
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);

            int slashIdx = storageRef.indexOf('/');
            String bucket = storageRef.substring(0, slashIdx);
            String key = storageRef.substring(slashIdx + 1);
            signRequest(conn, "DELETE", bucket, key, new byte[0], docProps);

            int status = conn.getResponseCode();
            if (status >= 200 && status < 300 || status == 404) {
                log.info("Document deleted: ref={}", storageRef);
            } else {
                log.error("Delete failed: ref={}, status={}", storageRef, status);
                throw new DocumentStorageException("Delete failed with HTTP " + status);
            }
        } catch (DocumentStorageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Delete error: ref={}, error={}", storageRef, e.getMessage());
            throw new DocumentStorageException("Delete failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check whether a document exists in MinIO/S3 via HEAD request.
     */
    public boolean documentExists(String storageRef) {
        if (storageRef == null || storageRef.isBlank()) {
            return false;
        }

        VarapiProperties.DocumentProperties docProps = varapiProperties.getDocument();
        String endpoint = docProps.getMinioEndpoint();

        try {
            String url = endpoint + "/" + storageRef;
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);

            int slashIdx = storageRef.indexOf('/');
            String bucket = storageRef.substring(0, slashIdx);
            String key = storageRef.substring(slashIdx + 1);
            signRequest(conn, "HEAD", bucket, key, new byte[0], docProps);

            int status = conn.getResponseCode();
            return status == 200;
        } catch (Exception e) {
            log.debug("Existence check failed for ref={}: {}", storageRef, e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // S3 Signature V4 signing (simplified for MinIO compatibility)
    // ------------------------------------------------------------------

    private void signRequest(HttpURLConnection conn, String method, String bucket, String key,
                             byte[] payload, VarapiProperties.DocumentProperties docProps) {
        try {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            String dateStamp = now.format(ISO_DATE);
            String amzDate = now.format(ISO_DATETIME);
            String payloadHash = sha256Hex(payload);

            conn.setRequestProperty("x-amz-date", amzDate);
            conn.setRequestProperty("x-amz-content-sha256", payloadHash);

            URI uri = URI.create(docProps.getMinioEndpoint());
            String host = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            conn.setRequestProperty("Host", host);

            String canonicalUri = "/" + bucket + "/" + key;
            String canonicalHeaders = "host:" + host + "\n"
                    + "x-amz-content-sha256:" + payloadHash + "\n"
                    + "x-amz-date:" + amzDate + "\n";
            String signedHeaders = "host;x-amz-content-sha256;x-amz-date";

            String canonicalRequest = method + "\n" + canonicalUri + "\n" + "\n"
                    + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;

            String scope = dateStamp + "/" + REGION + "/" + SERVICE + "/aws4_request";
            String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                    + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

            byte[] signingKey = getSignatureKey(docProps.getMinioSecretKey(), dateStamp);
            String signature = HexFormat.of().formatHex(hmacSha256(signingKey,
                    stringToSign.getBytes(StandardCharsets.UTF_8)));

            String auth = "AWS4-HMAC-SHA256 Credential=" + docProps.getMinioAccessKey() + "/" + scope
                    + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
            conn.setRequestProperty("Authorization", auth);
        } catch (Exception e) {
            log.warn("Failed to sign S3 request: {}", e.getMessage());
        }
    }

    private byte[] getSignatureKey(String secretKey, String dateStamp) throws Exception {
        byte[] kDate = hmacSha256(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8),
                dateStamp.getBytes(StandardCharsets.UTF_8));
        byte[] kRegion = hmacSha256(kDate, REGION.getBytes(StandardCharsets.UTF_8));
        byte[] kService = hmacSha256(kRegion, SERVICE.getBytes(StandardCharsets.UTF_8));
        return hmacSha256(kService, "aws4_request".getBytes(StandardCharsets.UTF_8));
    }

    private byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void ensureBucketExists() {
        VarapiProperties.DocumentProperties docProps = varapiProperties.getDocument();
        try {
            String url = docProps.getMinioEndpoint() + "/" + docProps.getMinioBucket();
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(3_000);
            conn.setReadTimeout(3_000);
            signRequest(conn, "HEAD", docProps.getMinioBucket(), "", new byte[0], docProps);

            int status = conn.getResponseCode();
            if (status == 404) {
                // Create bucket
                conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setRequestMethod("PUT");
                conn.setConnectTimeout(5_000);
                conn.setDoOutput(true);
                signRequest(conn, "PUT", docProps.getMinioBucket(), "", new byte[0], docProps);
                conn.getOutputStream().flush();
                int createStatus = conn.getResponseCode();
                if (createStatus >= 200 && createStatus < 300) {
                    log.info("Created MinIO bucket: {}", docProps.getMinioBucket());
                }
            } else {
                log.info("MinIO bucket exists: {}", docProps.getMinioBucket());
            }
        } catch (Exception e) {
            log.warn("MinIO bucket check failed (will retry on first upload): {}", e.getMessage());
        }
    }

    private String readResponseBody(HttpURLConnection conn) {
        try {
            InputStream is = conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream();
            return new String(readStream(is), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "(unable to read response)";
        }
    }

    private byte[] readStream(InputStream is) throws IOException {
        if (is == null) return new byte[0];
        try (is; ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = is.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            return buf.toByteArray();
        }
    }

    /** Exception for storage operation failures. */
    public static class DocumentStorageException extends RuntimeException {
        public DocumentStorageException(String message) { super(message); }
        public DocumentStorageException(String message, Throwable cause) { super(message, cause); }
    }
}
