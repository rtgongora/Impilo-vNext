package zw.gov.mohcc.impilo.landela.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.landela.core.AuditService;
import zw.gov.mohcc.impilo.landela.persistence.entity.DocumentAuditEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * Audit trail REST API for document operations.
 */
@RestController
@RequestMapping("/v1/internal/documents")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Get the full audit trail for a document.
     */
    @GetMapping("/{documentId}/audit")
    public ResponseEntity<ApiResponse<List<DocumentAuditEntity>>> getAuditTrail(
            @PathVariable UUID documentId) {

        TrustContext ctx = TrustContextHolder.require();

        List<DocumentAuditEntity> trail = auditService.getAuditTrail(documentId);
        return ResponseEntity.ok(
                ApiResponse.ok(trail, ctx.correlationId().toString()));
    }
}
