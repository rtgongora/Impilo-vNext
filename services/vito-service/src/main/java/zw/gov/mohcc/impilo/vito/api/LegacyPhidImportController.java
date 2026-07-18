package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.vito.core.LegacyPhidImportService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bulk legacy-PHID import (Identity Contract §14). INTERNAL-only admin endpoint
 * that reconciles a batch of MRS PHIDs into the vNext identity plane. Assigned
 * PHIDs become LEGACY_PHID aliases; unassigned pool numbers are RESERVED; the
 * same PHID against two people is QUARANTINED. Full provenance is recorded.
 */
@RestController
@RequestMapping("/internal/v1/legacy-phid")
public class LegacyPhidImportController {

    private final LegacyPhidImportService importService;

    public LegacyPhidImportController(LegacyPhidImportService importService) {
        this.importService = importService;
    }

    public record ImportRequestRecord(
            String phid, String sourceFacilityId, String issuanceDate,
            String givenName, String familyName, String sex,
            String dateOfBirth, String nationalId) {}

    public record ImportRequest(String sourceSystem, List<ImportRequestRecord> records) {}

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importBatch(
            @RequestBody ImportRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
            @RequestHeader(value = "X-Access-Mode", required = false) String accessMode) {

        if (!"INTERNAL".equalsIgnoreCase(accessMode)) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "INTERNAL_ONLY",
                    "message", "Legacy PHID import is an INTERNAL admin operation"));
        }

        List<LegacyPhidImportService.ImportRecord> records = request.records().stream()
                .map(r -> new LegacyPhidImportService.ImportRecord(
                        r.phid(), r.sourceFacilityId(), parseDate(r.issuanceDate()),
                        r.givenName(), r.familyName(), r.sex(),
                        parseDate(r.dateOfBirth()), r.nationalId()))
                .toList();

        LegacyPhidImportService.ImportResult result = importService.importBatch(
                UUID.fromString(tenantId), request.sourceSystem(), records,
                actorId != null ? actorId : "legacy-import");

        return ResponseEntity.ok(Map.of(
                "batchId", result.batchId().toString(),
                "total", result.total(),
                "assigned", result.assigned(),
                "reserved", result.reserved(),
                "quarantined", result.quarantined(),
                "invalid", result.invalid()));
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
