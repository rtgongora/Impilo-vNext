package zw.gov.mohcc.impilo.docstore.dto;

import java.util.UUID;

/**
 * DTO representing the result of an antivirus scan on a stored object.
 *
 * Status values: CLEAN, INFECTED, SKIPPED
 * The result field contains scanner-specific detail (e.g., virus name).
 */
public record ScanResult(
        UUID objectId,
        String status,
        String result
) {
}
