package zw.gov.mohcc.impilo.docstore.core.scan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.docstore.dto.ScanResult;

import java.util.UUID;

/**
 * No-op antivirus scanner implementation.
 *
 * Used when scanning is disabled or when the scanner type is set to NOOP.
 * Always returns CLEAN status without performing any actual scan.
 * Suitable for development and environments without ClamAV.
 */
@Service
@ConditionalOnProperty(
        name = "document-store.scan.scanner-type",
        havingValue = "NOOP",
        matchIfMissing = true
)
public class NoopScanService implements ScanService {

    private static final Logger log = LoggerFactory.getLogger(NoopScanService.class);

    @Override
    public ScanResult scan(UUID objectId, byte[] content) {
        log.debug("NOOP scan for object {}: skipping antivirus check", objectId);
        return new ScanResult(objectId, "CLEAN", "Scan skipped (NOOP scanner)");
    }
}
