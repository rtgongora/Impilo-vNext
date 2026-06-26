package zw.gov.mohcc.impilo.rito.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.rito.core.QualitySignalService;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.QualitySignalEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/rito/quality-signals")
public class RitoSignalController {

    private final QualitySignalService qualitySignalService;

    public RitoSignalController(QualitySignalService qualitySignalService) {
        this.qualitySignalService = qualitySignalService;
    }

    @PostMapping("")
    public ResponseEntity<QualitySignalEntity> ingest(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> detail = body.get("detail") instanceof Map
                ? (Map<String, Object>) body.get("detail") : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(qualitySignalService.ingest(
                tenantId,
                required(body, "signal_type"), required(body, "source_system"),
                str(body, "source_ref"), str(body, "severity"),
                uuid(body, "facility_id"), uuid(body, "district_id"),
                str(body, "summary"), detail));
    }

    @GetMapping("")
    public List<QualitySignalEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(required = false) String status) {
        return qualitySignalService.list(tenantId, status);
    }

    @PostMapping("/{id}/review")
    public QualitySignalEntity review(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        String reviewedBy = str(body, "reviewed_by");
        return qualitySignalService.review(tenantId, id,
                reviewedBy != null ? reviewedBy : actorId, boolVal(body, "dismiss"));
    }

    @PostMapping("/{id}/convert")
    public ResponseEntity<CaseEntity> convert(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestHeader(value = CompanionHeaders.ACTOR_TYPE, required = false) String actorType,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(qualitySignalService.convertToCase(
                tenantId, id, str(body, "case_type"), str(body, "severity"), actorId, actorType));
    }

    // ---- request-map helpers ----

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        return v.toString();
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? v.toString() : null;
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        return UUID.fromString(v.toString());
    }

    private static boolean boolVal(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        return v != null && Boolean.parseBoolean(v.toString());
    }
}
