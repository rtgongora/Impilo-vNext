package zw.gov.mohcc.impilo.oros.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.core.PrescriptionService;
import zw.gov.mohcc.impilo.oros.domain.RequestSource;
import zw.gov.mohcc.impilo.oros.persistence.entity.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The prescription aggregate API (OF-B2, Vol II §17.1).
 *
 * <p>Authoring (draft → sign+activate), amendment (new version, token rotation),
 * the token claim seam (§13.2 — dispensing vendors), and the compensation release.
 * The legacy pharmacy-service {@code POST /v1/prescriptions} silo is frozen (OD-13);
 * this controller is the sole authoring target.</p>
 */
@RestController
@RequestMapping("/v1/prescriptions")
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    public PrescriptionController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    // ── Request records ──────────────────────────────────────────────────

    public record ItemRequest(
            @NotBlank String medicationCode,
            String codingSystem,
            String displayName,
            String dose,
            String route,
            String frequency,
            Integer durationDays,
            BigDecimal quantity,
            String quantityUnit,
            String instructions,
            boolean controlled,
            Boolean substitutionPermitted
    ) {
        PrescriptionService.ItemData toData() {
            return new PrescriptionService.ItemData(medicationCode, codingSystem, displayName,
                    dose, route, frequency, durationDays, quantity, quantityUnit, instructions,
                    controlled, substitutionPermitted);
        }
    }

    public record CreatePrescriptionRequest(
            @NotBlank String patientCpid,
            String prescriberId,
            String prescriberName,
            String encounterRef,
            RequestSource requestSource,
            String sourceRef,
            Integer repeatsCeiling,
            LocalDate validityStart,
            LocalDate validityEnd,
            Boolean substitutionPermitted,
            String indication,
            @NotEmpty @Valid List<ItemRequest> items
    ) {}

    public record AmendPrescriptionRequest(@NotEmpty @Valid List<ItemRequest> items, @NotBlank String reason) {}

    public record UpdateDraftRequest(@NotEmpty @Valid List<ItemRequest> items) {}

    public record ReasonRequest(@NotBlank String reason) {}

    public record ClaimRequest(@NotBlank String token, String vendorRef) {}

    public record BindEpisodeRequest(@NotBlank String dispenseEpisodeRef) {}

    public record VerifyTokenRequest(@NotBlank String token) {}

    // ── Authoring ────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @Valid @RequestBody CreatePrescriptionRequest request) {
        String correlationId = correlationId();
        PrescriptionEntity rx = prescriptionService.create(new PrescriptionService.CreateData(
                request.patientCpid(), request.prescriberId(), request.prescriberName(),
                request.encounterRef(), request.requestSource(), request.sourceRef(),
                request.repeatsCeiling() != null ? request.repeatsCeiling() : 1,
                request.validityStart(), request.validityEnd(),
                request.substitutionPermitted() == null || request.substitutionPermitted(),
                request.indication(),
                request.items().stream().map(ItemRequest::toData).toList()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(summary(rx, null), correlationId));
    }

    @PutMapping("/{prescriptionId}/draft")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateDraft(
            @PathVariable String prescriptionId,
            @Valid @RequestBody UpdateDraftRequest request) {
        String correlationId = correlationId();
        PrescriptionVersionEntity head = prescriptionService.updateDraft(prescriptionId,
                request.items().stream().map(ItemRequest::toData).toList());
        PrescriptionEntity rx = prescriptionService.get(prescriptionId);
        return ResponseEntity.ok(ApiResponse.ok(summary(rx, head), correlationId));
    }

    /** Sign the unsigned head version and activate it atomically (§8.2.3); mints the token. */
    @PostMapping("/{prescriptionId}/sign")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sign(@PathVariable String prescriptionId) {
        String correlationId = correlationId();
        PrescriptionEntity rx = prescriptionService.signAndActivate(prescriptionId);
        return ResponseEntity.ok(ApiResponse.ok(summary(rx, null), correlationId));
    }

    /** Amend: creates the next UNSIGNED version — claimable only once signed (token rotates then). */
    @PostMapping("/{prescriptionId}/amend")
    public ResponseEntity<ApiResponse<Map<String, Object>>> amend(
            @PathVariable String prescriptionId,
            @Valid @RequestBody AmendPrescriptionRequest request) {
        String correlationId = correlationId();
        PrescriptionVersionEntity version = prescriptionService.amend(prescriptionId,
                request.items().stream().map(ItemRequest::toData).toList(), request.reason());
        PrescriptionEntity rx = prescriptionService.get(prescriptionId);
        return ResponseEntity.ok(ApiResponse.ok(summary(rx, version), correlationId));
    }

    @PostMapping("/{prescriptionId}/cancel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancel(
            @PathVariable String prescriptionId,
            @Valid @RequestBody ReasonRequest request) {
        String correlationId = correlationId();
        PrescriptionEntity rx = prescriptionService.cancel(prescriptionId, request.reason());
        return ResponseEntity.ok(ApiResponse.ok(summary(rx, null), correlationId));
    }

    // ── Reads ────────────────────────────────────────────────────────────

    @GetMapping("/{prescriptionId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable String prescriptionId) {
        String correlationId = correlationId();
        PrescriptionEntity rx = prescriptionService.get(prescriptionId);
        Map<String, Object> body = summary(rx, null);
        if (rx.getCurrentVersionId() != null) {
            body.put("items", prescriptionService.itemsOfVersion(rx.getCurrentVersionId()).stream()
                    .map(PrescriptionController::itemView).toList());
        }
        return ResponseEntity.ok(ApiResponse.ok(body, correlationId));
    }

    @GetMapping("/{prescriptionId}/versions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> versions(@PathVariable String prescriptionId) {
        String correlationId = correlationId();
        List<Map<String, Object>> versions = prescriptionService.versions(prescriptionId).stream()
                .map(PrescriptionController::versionView).toList();
        return ResponseEntity.ok(ApiResponse.ok(versions, correlationId));
    }

    /** The active token for patient/prescriber surfaces (QR payload = tokenJws, nothing else). */
    @GetMapping("/{prescriptionId}/token")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activeToken(@PathVariable String prescriptionId) {
        String correlationId = correlationId();
        PrescriptionTokenEntity token = prescriptionService.activeToken(prescriptionId)
                .orElseThrow(() -> new zw.gov.mohcc.impilo.oros.core.OrosDomainException(
                        "NO_ACTIVE_TOKEN", 404, "No active token for this prescription."));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tokenId", token.getTokenId());
        body.put("tokenJws", token.getTokenJws());
        body.put("expiresAt", token.getExpiresAt());
        return ResponseEntity.ok(ApiResponse.ok(body, correlationId));
    }

    @GetMapping("/patient/{patientCpid}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> forPatient(@PathVariable String patientCpid) {
        String correlationId = correlationId();
        List<Map<String, Object>> list = prescriptionService.forPatient(patientCpid).stream()
                .map(rx -> summary(rx, null)).toList();
        return ResponseEntity.ok(ApiResponse.ok(list, correlationId));
    }

    // ── Claim seam (dispensing vendors, §13.2.3) ─────────────────────────

    @PostMapping("/claims")
    public ResponseEntity<ApiResponse<Map<String, Object>>> claim(
            @Valid @RequestBody ClaimRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String correlationId = correlationId();
        PrescriptionService.ClaimResult result =
                prescriptionService.claim(request.token(), idempotencyKey, request.vendorRef());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("claimId", result.claim().getClaimId());
        body.put("claimStatus", result.claim().getStatus());
        body.put("duplicateReplay", result.duplicateReplay());
        body.put("prescriptionId", result.prescription().getPrescriptionId());
        body.put("prescriptionVersionId", result.claim().getPrescriptionVersionId());
        body.put("patientCpid", result.prescription().getPatientCpid());
        body.put("repeatsRemaining", result.prescription().getRepeatsRemaining());
        body.put("controlled", result.prescription().isControlled());
        body.put("substitutionPermitted", result.prescription().isSubstitutionPermitted());
        // The authoritative lines — retrieved server-side, never carried in the token.
        body.put("lines", result.lines().stream().map(PrescriptionController::itemView).toList());
        return ResponseEntity.status(result.duplicateReplay() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.ok(body, correlationId));
    }

    /**
     * OF-B12 §13.3.1: bind the pharmacy dispense episode onto the claim — internal
     * seam called by pharmacy-service on episode completion. Idempotent on the same
     * reference; 409 when already bound to a different episode.
     */
    @PostMapping("/claims/{claimId}/bind-episode")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bindEpisode(
            @PathVariable UUID claimId,
            @Valid @RequestBody BindEpisodeRequest request) {
        String correlationId = correlationId();
        PrescriptionClaimEntity claim =
                prescriptionService.bindDispenseEpisode(claimId, request.dispenseEpisodeRef());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("claimId", claim.getClaimId());
        body.put("claimStatus", claim.getStatus());
        body.put("dispenseEpisodeRef", claim.getDispenseEpisodeRef());
        body.put("prescriptionId", claim.getPrescriptionId());
        return ResponseEntity.ok(ApiResponse.ok(body, correlationId));
    }

    /** Compensation: restore the repeats counter when an episode fails before dispensing (§9.2). */
    @PostMapping("/claims/{claimId}/release")
    public ResponseEntity<ApiResponse<Map<String, Object>>> releaseClaim(
            @PathVariable UUID claimId,
            @Valid @RequestBody ReasonRequest request) {
        String correlationId = correlationId();
        PrescriptionClaimEntity claim = prescriptionService.releaseClaim(claimId, request.reason());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("claimId", claim.getClaimId());
        body.put("claimStatus", claim.getStatus());
        body.put("releaseReason", claim.getReleaseReason());
        return ResponseEntity.ok(ApiResponse.ok(body, correlationId));
    }

    /** Dispenser pre-check: token standing WITHOUT claiming. */
    @PostMapping("/tokens/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyToken(
            @Valid @RequestBody VerifyTokenRequest request) {
        String correlationId = correlationId();
        return ResponseEntity.ok(ApiResponse.ok(
                new LinkedHashMap<>(prescriptionService.verifyToken(request.token())), correlationId));
    }

    // ── Views ────────────────────────────────────────────────────────────

    private static Map<String, Object> summary(PrescriptionEntity rx, PrescriptionVersionEntity version) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("prescriptionId", rx.getPrescriptionId());
        m.put("patientCpid", rx.getPatientCpid());
        m.put("prescriberId", rx.getPrescriberId());
        m.put("status", rx.getStatus().name());
        m.put("repeatsCeiling", rx.getRepeatsCeiling());
        m.put("repeatsRemaining", rx.getRepeatsRemaining());
        m.put("validityStart", rx.getValidityStart());
        m.put("validityEnd", rx.getValidityEnd());
        m.put("controlled", rx.isControlled());
        m.put("substitutionPermitted", rx.isSubstitutionPermitted());
        m.put("currentVersionId", rx.getCurrentVersionId());
        m.put("requestSource", rx.getRequestSource() != null ? rx.getRequestSource().name() : null);
        m.put("sourceRef", rx.getSourceRef());
        if (version != null) {
            m.put("headVersion", versionView(version));
        }
        return m;
    }

    private static Map<String, Object> versionView(PrescriptionVersionEntity v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("prescriptionVersionId", v.getPrescriptionVersionId());
        m.put("version", v.getVersion());
        m.put("supersedesVersionId", v.getSupersedesVersionId());
        m.put("contentHash", v.getContentHash());
        m.put("signed", v.isSigned());
        m.put("signedBy", v.getSignedBy());
        m.put("signedAt", v.getSignedAt());
        m.put("reason", v.getReason());
        return m;
    }

    private static Map<String, Object> itemView(PrescriptionItemEntity i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("medicationCode", i.getMedicationCode());
        m.put("codingSystem", i.getCodingSystem());
        m.put("displayName", i.getDisplayName());
        m.put("dose", i.getDose());
        m.put("route", i.getRoute());
        m.put("frequency", i.getFrequency());
        m.put("durationDays", i.getDurationDays());
        m.put("quantity", i.getQuantity());
        m.put("quantityUnit", i.getQuantityUnit());
        m.put("instructions", i.getInstructions());
        m.put("controlled", i.isControlled());
        m.put("substitutionPermitted", i.isSubstitutionPermitted());
        return m;
    }

    private static String correlationId() {
        return TrustContextHolder.require().correlationId().toString();
    }
}
