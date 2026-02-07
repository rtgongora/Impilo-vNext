package zw.gov.mohcc.impilo.docstore.core.scan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.docstore.dto.ScanResult;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * ClamAV antivirus scanner implementation.
 *
 * Connects to a ClamAV daemon via TCP socket using the INSTREAM protocol.
 * The daemon must be accessible at the configured host and port.
 *
 * ClamAV INSTREAM protocol:
 *   1. Send "zINSTREAM\0"
 *   2. Send chunks: 4-byte big-endian length prefix + data
 *   3. Send 4-byte zero-length to signal end
 *   4. Read response: "stream: OK\0" or "stream: <virus> FOUND\0"
 *
 * Active only when document-store.scan.scanner-type=CLAMAV.
 */
@Service
@ConditionalOnProperty(
        name = "document-store.scan.scanner-type",
        havingValue = "CLAMAV"
)
public class ClamAvScanService implements ScanService {

    private static final Logger log = LoggerFactory.getLogger(ClamAvScanService.class);

    private static final String CLAMAV_HOST = "localhost";
    private static final int CLAMAV_PORT = 3310;
    private static final int CHUNK_SIZE = 2048;
    private static final byte[] INSTREAM_CMD = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZERO_LENGTH = new byte[]{0, 0, 0, 0};

    @Override
    public ScanResult scan(UUID objectId, byte[] content) {
        log.info("ClamAV scan started for object {}, size={} bytes", objectId, content.length);

        try (Socket socket = new Socket(CLAMAV_HOST, CLAMAV_PORT)) {
            socket.setSoTimeout(30_000);

            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            InputStream in = socket.getInputStream();

            // Send INSTREAM command
            out.write(INSTREAM_CMD);

            // Send file data in chunks with 4-byte big-endian length prefix
            int offset = 0;
            while (offset < content.length) {
                int chunkLen = Math.min(CHUNK_SIZE, content.length - offset);
                byte[] lengthPrefix = ByteBuffer.allocate(4)
                        .order(ByteOrder.BIG_ENDIAN)
                        .putInt(chunkLen)
                        .array();
                out.write(lengthPrefix);
                out.write(content, offset, chunkLen);
                offset += chunkLen;
            }

            // Send zero-length chunk to signal end of stream
            out.write(ZERO_LENGTH);
            out.flush();

            // Read response
            byte[] responseBytes = in.readAllBytes();
            String response = new String(responseBytes, StandardCharsets.US_ASCII).trim();
            response = response.replace("\0", "");

            log.info("ClamAV response for object {}: {}", objectId, response);

            if (response.contains("OK")) {
                return new ScanResult(objectId, "CLEAN", response);
            } else if (response.contains("FOUND")) {
                return new ScanResult(objectId, "INFECTED", response);
            } else {
                log.warn("Unexpected ClamAV response for object {}: {}", objectId, response);
                return new ScanResult(objectId, "PENDING", "Unexpected scanner response: " + response);
            }

        } catch (IOException e) {
            log.error("ClamAV scan failed for object {}: {}", objectId, e.getMessage(), e);
            return new ScanResult(objectId, "PENDING", "Scan failed: " + e.getMessage());
        }
    }
}
