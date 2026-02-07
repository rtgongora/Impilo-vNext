package zw.gov.mohcc.impilo.docstore.core.scan;

import zw.gov.mohcc.impilo.docstore.dto.ScanResult;

import java.util.UUID;

/**
 * Antivirus scanning interface for uploaded documents.
 *
 * Implementations scan binary content and return a ScanResult indicating
 * whether the file is CLEAN, INFECTED, or SKIPPED. The active implementation
 * is selected based on the document-store.scan.scannerType configuration.
 */
public interface ScanService {

    /**
     * Scan the given file content for malware.
     *
     * @param objectId the UUID of the stored object
     * @param content  the raw bytes of the file
     * @return scan result with status and optional detail
     */
    ScanResult scan(UUID objectId, byte[] content);
}
