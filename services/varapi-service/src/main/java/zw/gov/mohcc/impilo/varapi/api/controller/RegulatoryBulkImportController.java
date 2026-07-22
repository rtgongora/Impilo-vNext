package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.core.RegulatoryBulkImportService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.RegulatoryImportBatchEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.RegulatoryImportRowEntity;

import java.util.List;
import java.util.Map;

/**
 * Regulatory bulk-import rail (ROM-U7). A council uploads its existing data (registered/licensed
 * people + conditions of practice; personnel) — it is STAGED, MATCHED against live truth, REVIEWED
 * row-by-row, then APPLIED (register data only — grants no authority).
 */
@RestController
@RequestMapping("/v1/internal/regulatory/imports")
public class RegulatoryBulkImportController {

    private final RegulatoryBulkImportService service;

    public RegulatoryBulkImportController(RegulatoryBulkImportService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<RegulatoryImportBatchEntity>> batches() {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(service.batches(ctx.tenantId()));
    }

    @SuppressWarnings("unchecked")
    @PostMapping
    public ResponseEntity<RegulatoryImportBatchEntity> stage(@RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> rows = body.get("rows") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.stage(
                ctx.tenantId(), asLong(body.get("councilId")), str(body.get("recordType")),
                str(body.get("sourceLabel")), str(body.get("filename")), ctx.actorId(), rows));
    }

    @GetMapping("/{batchId}/rows")
    public ResponseEntity<List<RegulatoryImportRowEntity>> rows(@PathVariable Long batchId) {
        return ResponseEntity.ok(service.rows(batchId));
    }

    @PostMapping("/rows/{rowId}/review")
    public ResponseEntity<RegulatoryImportRowEntity> review(@PathVariable Long rowId, @RequestBody Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        boolean accept = "ACCEPTED".equalsIgnoreCase(str(body.get("decision")))
                || Boolean.TRUE.equals(body.get("accept"));
        return ResponseEntity.ok(service.review(ctx.tenantId(), rowId, accept));
    }

    @PostMapping("/{batchId}/apply")
    public ResponseEntity<RegulatoryImportBatchEntity> apply(@PathVariable Long batchId) {
        TrustContext ctx = TrustContextHolder.require();
        return ResponseEntity.ok(service.apply(ctx.tenantId(), batchId));
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static Long asLong(Object o) { try { return o == null ? null : Long.valueOf(o.toString()); } catch (NumberFormatException e) { return null; } }
}
