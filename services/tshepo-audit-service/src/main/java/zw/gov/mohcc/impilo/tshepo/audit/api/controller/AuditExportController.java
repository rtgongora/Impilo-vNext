package zw.gov.mohcc.impilo.tshepo.audit.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditExportRequest;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditExportResponse;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.ExportVerificationResponse;
import zw.gov.mohcc.impilo.tshepo.audit.core.AuditExportService;

import java.util.Optional;
import java.util.UUID;

/**
 * Endpoints for creating and verifying signed audit export bundles.
 * Exports provide legal defensibility through Ed25519-signed hash bundles.
 */
@RestController
@RequestMapping("/v1/audit")
public class AuditExportController {

    private final AuditExportService exportService;

    public AuditExportController(AuditExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * POST /v1/audit/export — create a signed export bundle for a range of audit events.
     */
    @PostMapping("/export")
    public ResponseEntity<ApiResponse<AuditExportResponse>> createExport(
            @Valid @RequestBody AuditExportRequest request,
            @RequestHeader(value = "x-correlation-id", required = false) UUID correlationId) {

        String corrId = correlationId != null ? correlationId.toString() : UUID.randomUUID().toString();

        AuditExportResponse response = exportService.createExport(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, corrId));
    }

    /**
     * GET /v1/audit/export/{id} — retrieve export bundle metadata.
     */
    @GetMapping("/export/{id}")
    public ResponseEntity<ApiResponse<AuditExportResponse>> getExport(
            @PathVariable UUID id,
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader(value = "x-correlation-id", required = false) UUID correlationId) {

        String corrId = correlationId != null ? correlationId.toString() : UUID.randomUUID().toString();

        Optional<AuditExportResponse> result = exportService.getExport(tenantId, id);

        return result.map(export ->
                ResponseEntity.ok(ApiResponse.ok(export, corrId))
        ).orElseGet(() ->
                ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND",
                                "Export bundle not found: " + id, 404, corrId))
        );
    }

    /**
     * GET /v1/audit/verify/{id} — verify the integrity of a signed export bundle.
     * Re-fetches the events, recomputes the bundle hash, and compares.
     */
    @GetMapping("/verify/{id}")
    public ResponseEntity<ApiResponse<ExportVerificationResponse>> verifyExport(
            @PathVariable UUID id,
            @RequestHeader("x-tenant-id") UUID tenantId,
            @RequestHeader(value = "x-correlation-id", required = false) UUID correlationId) {

        String corrId = correlationId != null ? correlationId.toString() : UUID.randomUUID().toString();

        ExportVerificationResponse result = exportService.verifyExport(tenantId, id);

        HttpStatus status = result.intact() ? HttpStatus.OK : HttpStatus.CONFLICT;
        return ResponseEntity.status(status)
                .body(ApiResponse.ok(result, corrId));
    }
}
