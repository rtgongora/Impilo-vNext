package zw.gov.mohcc.impilo.experience.intake;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry intake orchestration API — progressive sessions, governed bulk import,
 * self-help recovery tickets, and day-zero bootstrap snapshot (read-only probes).
 * <p>
 * When a session completes and a patient identity is materialised, downstream flows should synchronise
 * <strong>communication preferences</strong> in mvumo-service (versioned {@code /internal/v1/mvumo/.../communication-preferences*},
 * or consent-summary for read surfaces) so intake does not treat preferences as one-time registration fields.
 * Desk and facility intake UIs can call the same BFF-proxied Mvumo routes as the citizen portal and EHR.
 */
@RestController
@RequestMapping("/internal/v1/registry-intake")
public class RegistryIntakeController {

    private final RegistryIntakeService intakeService;

    public RegistryIntakeController(RegistryIntakeService intakeService) {
        this.intakeService = intakeService;
    }

    @GetMapping("/bootstrap/snapshot")
    public ResponseEntity<Map<String, Object>> bootstrapSnapshot(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ok(requestId, correlationId, intakeService.bootstrapSnapshot());
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        String initiatedBy = actorId != null && !actorId.isBlank() ? actorId : "anonymous";
        String intakeType = str(body, "intakeType");
        String targetRegistry = str(body, "targetRegistry");
        String channel = str(body, "initiatedChannel", "channel");
        if (intakeType.isBlank() || targetRegistry.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "intakeType and targetRegistry are required")));
        }
        RegistryIntakeDocuments.IntakeSessionDocument doc = intakeService.createSession(
                intakeType,
                targetRegistry,
                channel.isBlank() ? "WEB" : channel,
                initiatedBy,
                str(body, "provenance"),
                str(body, "notes"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", doc,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<Map<String, Object>> getSession(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return intakeService.getSession(id)
                .map(d -> ok(requestId, correlationId, d))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", Map.of("code", "NOT_FOUND", "message", "Intake session not found"),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId))));
    }

    @PatchMapping("/sessions/{id}")
    public ResponseEntity<Map<String, Object>> patchSession(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return intakeService.patchSession(
                        id,
                        str(body, "workflowState"),
                        str(body, "completionState"),
                        str(body, "linkedHealthId"),
                        str(body, "linkedEntityId"),
                        str(body, "notes"))
                .map(d -> ok(requestId, correlationId, d))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", Map.of("code", "NOT_FOUND", "message", "Intake session not found"),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId))));
    }

    @PostMapping("/recovery-requests")
    public ResponseEntity<Map<String, Object>> createRecovery(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        String requestedBy = actorId != null && !actorId.isBlank() ? actorId : "anonymous";
        String kind = str(body, "requestKind", "kind");
        String target = str(body, "targetRegistry");
        if (kind.isBlank() || target.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "requestKind and targetRegistry are required")));
        }
        RegistryIntakeDocuments.RecoveryRequestDocument doc = intakeService.createRecoveryRequest(
                kind,
                target,
                requestedBy,
                str(body, "contactHint", "contact"),
                str(body, "narrative", "notes"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "data", doc,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/recovery-requests/{id}")
    public ResponseEntity<Map<String, Object>> getRecovery(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return intakeService.getRecovery(id)
                .map(d -> ok(requestId, correlationId, d))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", Map.of("code", "NOT_FOUND", "message", "Recovery request not found"),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId))));
    }

    @PostMapping("/import-jobs")
    public ResponseEntity<Map<String, Object>> createImportJob(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        String createdBy = actorId != null && !actorId.isBlank() ? actorId : "anonymous";
        try {
            String importType = str(body, "importType");
            if (importType.isBlank()) {
                importType = "FACILITY_CSV";
            }
            RegistryIntakeDocuments.ImportJobDocument job = intakeService.createImportJob(
                    importType,
                    str(body, "targetRegistry"),
                    str(body, "csv", "csvText"),
                    str(body, "landelaDocumentId", "landelaDocumentUUID", "documentId"),
                    createdBy,
                    str(body, "notes"));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", job,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/import-jobs/{id}")
    public ResponseEntity<Map<String, Object>> getImportJob(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return intakeService.getImportJob(id)
                .map(d -> ok(requestId, correlationId, d))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", Map.of("code", "NOT_FOUND", "message", "Import job not found"),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId))));
    }

    @PostMapping("/import-jobs/{id}/execute")
    public ResponseEntity<Map<String, Object>> executeImportJob(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        boolean dryRun = body != null && Boolean.TRUE.equals(body.get("dryRun"));
        try {
            RegistryIntakeDocuments.ImportJobDocument job = intakeService.executeImportJob(id, dryRun);
            return ok(requestId, correlationId, job);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                    "error", Map.of("code", "PAYLOAD_EXPIRED", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    @GetMapping("/indawo/sites")
    public ResponseEntity<Map<String, Object>> searchIndawoSites(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "cursor", defaultValue = "0") int cursor,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ok(requestId, correlationId, intakeService.searchIndawoSites(query, cursor, limit));
    }

    @PostMapping("/providers/from-health-id")
    public ResponseEntity<Map<String, Object>> createProviderFromHealthId(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            return ok(requestId, correlationId, intakeService.createProviderFromHealthId(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    private static ResponseEntity<Map<String, Object>> ok(
            String requestId, String correlationId, Object data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("data", data);
        envelope.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(envelope);
    }

    private static String str(Map<String, ?> body, String... keys) {
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null) {
                return v.toString();
            }
        }
        return "";
    }
}
