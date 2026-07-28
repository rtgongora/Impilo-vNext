package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.PctDomainException;
import zw.gov.mohcc.impilo.pct.core.ReferralPackageService;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReferralPackageEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/referrals")
@Profile("legacy-referrals")
public class ReferralController {
    private final ReferralPackageService referralService;

    public ReferralController(ReferralPackageService referralService) {
        this.referralService = referralService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReferralPackageEntity>> create(@RequestBody Map<String, Object> request) {
        return execute(() -> referralService.createDraft(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReferralPackageEntity>> get(@PathVariable UUID id) {
        return execute(() -> referralService.get(id));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReferralPackageEntity>>> listPatient(
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String status) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        if (patientId == null || patientId.isBlank()) {
            return ResponseEntity.ok(ApiResponse.ok(referralService.listOperational(status), correlationId));
        }
        return ResponseEntity.ok(ApiResponse.ok(referralService.listByPatient(patientId, status), correlationId));
    }

    @GetMapping("/incoming")
    public ResponseEntity<ApiResponse<List<ReferralPackageEntity>>> listIncoming(
            @RequestParam String facilityId,
            @RequestParam(required = false) String status) {
        return executeList(() -> referralService.listIncoming(UUID.fromString(facilityId), status));
    }

    @GetMapping("/encounter/{encounterId}")
    public ResponseEntity<ApiResponse<List<ReferralPackageEntity>>> listEncounter(@PathVariable Long encounterId) {
        return executeList(() -> referralService.listByEncounter(encounterId));
    }

    @PutMapping("/{id}/stage")
    public ResponseEntity<ApiResponse<ReferralPackageEntity>> updateStage(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request) {
        int stage = parseStage(request);
        return execute(() -> referralService.updateStage(id, stage, request));
    }

    @PostMapping("/{id}/consent")
    public ResponseEntity<ApiResponse<ReferralPackageEntity>> updateConsent(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request) {
        return execute(() -> referralService.updateConsent(id, request));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<ReferralPackageEntity>> submit(@PathVariable UUID id) {
        return execute(() -> referralService.submit(id));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<ReferralPackageEntity>> accept(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> request) {
        return execute(() -> referralService.accept(id, request == null ? Map.of() : request));
    }

    @PostMapping("/{id}/respond")
    public ResponseEntity<ApiResponse<ReferralPackageEntity>> respond(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> request) {
        return execute(() -> referralService.respond(id, request));
    }

    /**
     * Marks the consultation as begun. Called when the first participant is admitted from the
     * waiting room; idempotent, so a reconnect does not reset the start time.
     */
    @PostMapping("/{id}/session-started")
    public ResponseEntity<ApiResponse<ReferralPackageEntity>> markSessionStarted(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> request) {
        Object at = request == null ? null : request.get("started_at");
        java.time.OffsetDateTime startedAt = at == null || at.toString().isBlank()
                ? null : java.time.OffsetDateTime.parse(at.toString());
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(
                referralService.markSessionStarted(id, startedAt), correlationId));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<ReferralPackageEntity>> complete(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> request) {
        return execute(() -> referralService.complete(id, request == null ? Map.of() : request));
    }

    private int parseStage(Map<String, Object> request) {
        Object stage = request.getOrDefault("stage", request.get("workflow_stage"));
        if (stage == null) throw new PctDomainException("INVALID_STAGE", 400, "stage is required");
        try {
            return Integer.parseInt(stage.toString());
        } catch (NumberFormatException ex) {
            throw new PctDomainException("INVALID_STAGE", 400, "stage must be numeric");
        }
    }

    private ResponseEntity<ApiResponse<ReferralPackageEntity>> execute(ThrowingSupplier<ReferralPackageEntity> action) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        try {
            return ResponseEntity.ok(ApiResponse.ok(action.get(), correlationId));
        } catch (PctDomainException ex) {
            return ResponseEntity.status(ex.getStatus())
                    .body(ApiResponse.error(ex.getCode(), ex.getMessage(), ex.getStatus(), correlationId));
        }
    }

    private ResponseEntity<ApiResponse<List<ReferralPackageEntity>>> executeList(ThrowingSupplier<List<ReferralPackageEntity>> action) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        try {
            return ResponseEntity.ok(ApiResponse.ok(action.get(), correlationId));
        } catch (PctDomainException ex) {
            return ResponseEntity.status(ex.getStatus())
                    .body(ApiResponse.error(ex.getCode(), ex.getMessage(), ex.getStatus(), correlationId));
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get();
    }
}
