package zw.gov.mohcc.impilo.cardprint.service.spooler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Network printer spooler using IPP (Internet Printing Protocol, RFC 8011).
 * <p>
 * Sends rendered card PDFs to a network printer via IPP over HTTP/HTTPS.
 * Compatible with IPP-capable CR-80 card printers (Fargo, Magicard, Evolis)
 * and CUPS-based print servers.
 * <p>
 * Configuration:
 * <ul>
 *   <li>{@code card-print.spooler.type=NETWORK} — activates this provider</li>
 *   <li>{@code card-print.spooler.printer-uri} — IPP printer URI (e.g. ipp://printer:631/ipp/print)</li>
 *   <li>{@code card-print.spooler.cache-dir} — local cache directory for sent PDFs</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "card-print.spooler.type", havingValue = "NETWORK")
public class NetworkSpoolerService implements SpoolerService {

    private static final Logger log = LoggerFactory.getLogger(NetworkSpoolerService.class);

    private static final int IPP_VERSION_MAJOR = 1;
    private static final int IPP_VERSION_MINOR = 1;
    private static final short OP_PRINT_JOB = 0x0002;
    private static final byte TAG_OPERATION = 0x01;
    private static final byte TAG_JOB = 0x02;
    private static final byte TAG_END = 0x03;
    private static final byte VALUE_CHARSET = 0x47;
    private static final byte VALUE_NATURAL_LANG = 0x48;
    private static final byte VALUE_URI = 0x45;
    private static final byte VALUE_NAME = 0x42;
    private static final byte VALUE_MIME = 0x49;

    private final String printerUri;
    private final Path cacheDir;

    public NetworkSpoolerService(
            @Value("${card-print.spooler.printer-uri:ipp://localhost:631/ipp/print}") String printerUri,
            @Value("${card-print.spooler.cache-dir:${java.io.tmpdir}/card-print-cache}") String cacheDir)
            throws IOException {
        this.printerUri = printerUri;
        this.cacheDir = Path.of(cacheDir);
        Files.createDirectories(this.cacheDir);
        log.info("Network spooler initialised: printer={}, cache={}", printerUri, this.cacheDir);
    }

    @Override
    public void spool(UUID jobId, String fileName, byte[] pdfData) throws IOException {
        // Cache the PDF locally for retrieval
        Path cached = cacheDir.resolve(jobId.toString() + "_" + fileName);
        Files.write(cached, pdfData);

        // Build IPP Print-Job request
        byte[] ippRequest = buildPrintJobRequest(jobId, fileName, pdfData);

        // Convert ipp:// URI to http:// for transport
        String httpUri = printerUri
                .replaceFirst("^ipp://", "http://")
                .replaceFirst("^ipps://", "https://");

        HttpURLConnection conn = (HttpURLConnection) URI.create(httpUri).toURL().openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            conn.setRequestProperty("Content-Type", "application/ipp");
            conn.setRequestProperty("Content-Length", String.valueOf(ippRequest.length));

            try (OutputStream out = conn.getOutputStream()) {
                out.write(ippRequest);
                out.flush();
            }

            int status = conn.getResponseCode();
            if (status == 200) {
                byte[] responseBody = readStream(conn.getInputStream());
                int ippStatus = parseIppStatusCode(responseBody);
                if (ippStatus <= 0x00FF) {
                    log.info("Print job spooled successfully: jobId={}, file={}, ippStatus=0x{}",
                            jobId, fileName, Integer.toHexString(ippStatus));
                } else {
                    log.error("IPP error for job {}: status=0x{}", jobId, Integer.toHexString(ippStatus));
                    throw new IOException("IPP print job failed with status 0x" + Integer.toHexString(ippStatus));
                }
            } else {
                String errorBody = new String(readStream(conn.getErrorStream()), StandardCharsets.UTF_8);
                log.error("HTTP error spooling print job {}: status={}, body={}", jobId, status, errorBody);
                throw new IOException("Network printer returned HTTP " + status);
            }
        } finally {
            conn.disconnect();
        }
    }

    @Override
    public byte[] retrieve(UUID jobId, String fileName) throws IOException {
        Path cached = cacheDir.resolve(jobId.toString() + "_" + fileName);
        if (Files.exists(cached)) {
            return Files.readAllBytes(cached);
        }
        log.warn("Cached PDF not found for job {}: {}", jobId, cached);
        return null;
    }

    /**
     * Builds a minimal IPP Print-Job request (RFC 8011 §4.2.1).
     */
    private byte[] buildPrintJobRequest(UUID jobId, String fileName, byte[] pdfData) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(pdfData.length + 512);

        // IPP version
        buf.write(IPP_VERSION_MAJOR);
        buf.write(IPP_VERSION_MINOR);

        // Operation ID: Print-Job
        buf.write((OP_PRINT_JOB >> 8) & 0xFF);
        buf.write(OP_PRINT_JOB & 0xFF);

        // Request ID
        int requestId = jobId.hashCode() & 0x7FFFFFFF;
        writeInt(buf, requestId);

        // Operation attributes group
        buf.write(TAG_OPERATION);

        // attributes-charset
        writeAttribute(buf, VALUE_CHARSET, "attributes-charset", "utf-8");

        // attributes-natural-language
        writeAttribute(buf, VALUE_NATURAL_LANG, "attributes-natural-language", "en");

        // printer-uri
        writeAttribute(buf, VALUE_URI, "printer-uri", printerUri);

        // job-name
        writeAttribute(buf, VALUE_NAME, "job-name", fileName);

        // document-format
        writeAttribute(buf, VALUE_MIME, "document-format", "application/pdf");

        // Job attributes group
        buf.write(TAG_JOB);

        // requesting-user-name
        writeAttribute(buf, VALUE_NAME, "requesting-user-name", "impilo-card-print");

        // End of attributes
        buf.write(TAG_END);

        // Document data
        buf.write(pdfData, 0, pdfData.length);

        return buf.toByteArray();
    }

    private void writeAttribute(ByteArrayOutputStream buf, byte valueTag, String name, String value) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        buf.write(valueTag);
        buf.write((nameBytes.length >> 8) & 0xFF);
        buf.write(nameBytes.length & 0xFF);
        buf.write(nameBytes, 0, nameBytes.length);
        buf.write((valueBytes.length >> 8) & 0xFF);
        buf.write(valueBytes.length & 0xFF);
        buf.write(valueBytes, 0, valueBytes.length);
    }

    private void writeInt(ByteArrayOutputStream buf, int value) {
        buf.write((value >> 24) & 0xFF);
        buf.write((value >> 16) & 0xFF);
        buf.write((value >> 8) & 0xFF);
        buf.write(value & 0xFF);
    }

    private int parseIppStatusCode(byte[] response) {
        if (response.length >= 4) {
            return ((response[2] & 0xFF) << 8) | (response[3] & 0xFF);
        }
        return 0xFFFF;
    }

    private byte[] readStream(InputStream is) throws IOException {
        if (is == null) return new byte[0];
        try (is; ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int n;
            while ((n = is.read(chunk)) != -1) {
                buf.write(chunk, 0, n);
            }
            return buf.toByteArray();
        }
    }
}
