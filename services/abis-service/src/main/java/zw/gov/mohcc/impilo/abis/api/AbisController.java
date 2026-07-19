package zw.gov.mohcc.impilo.abis.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.abis.api.dto.EnrollTemplateRequest;
import zw.gov.mohcc.impilo.abis.api.dto.EnrollTemplateResponse;
import zw.gov.mohcc.impilo.abis.api.dto.IdentifyRequest;
import zw.gov.mohcc.impilo.abis.api.dto.IdentifyResponse;
import zw.gov.mohcc.impilo.abis.api.dto.VerifyRequest;
import zw.gov.mohcc.impilo.abis.api.dto.VerifyResponse;
import zw.gov.mohcc.impilo.abis.core.AbisTemplateService;
import zw.gov.mohcc.impilo.abis.engine.BiometricMatchingEngine.IdentificationCandidate;
import zw.gov.mohcc.impilo.abis.engine.BiometricMatchingEngine.VerificationDecision;
import zw.gov.mohcc.impilo.abis.engine.BiometricProbeContext;
import zw.gov.mohcc.impilo.abis.persistence.entity.TemplateEntity;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ABIS REST API (Identity Contract §11).
 *
 * <ul>
 *   <li>{@code POST /v1/abis/templates} — enroll an encrypted template (opaque subject_ref).</li>
 *   <li>{@code POST /v1/abis/verify} — routine 1:1 verification (fail-closed → UNAVAILABLE).</li>
 *   <li>{@code POST /v1/abis/identify} — restricted 1:N; requires
 *       {@code X-Identify-Reason} ∈ {ENROLMENT, RECOVERY, DEDUPLICATION}; returns candidates
 *       for adjudication, NEVER an automatic merge.</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/abis")
public class AbisController {

    static final String IDENTIFY_REASON_HEADER = "X-Identify-Reason";
    static final Set<String> ALLOWED_IDENTIFY_REASONS = Set.of("ENROLMENT", "RECOVERY", "DEDUPLICATION");

    private final AbisTemplateService templateService;

    public AbisController(AbisTemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping("/templates")
    public ResponseEntity<EnrollTemplateResponse> enroll(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @Valid @RequestBody EnrollTemplateRequest request) {
        TemplateEntity saved = templateService.enroll(
                tenantId,
                request.subjectRef(),
                request.modality(),
                request.position(),
                request.qualityScore(),
                request.algorithmVersion(),
                Base64.getDecoder().decode(request.templateBase64()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new EnrollTemplateResponse(
                saved.getId(),
                saved.getSubjectRef(),
                saved.getModality().name(),
                saved.getPosition(),
                saved.getStatus()));
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifyResponse> verify(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @Valid @RequestBody VerifyRequest request) {
        VerificationDecision decision = templateService.verify(
                tenantId,
                request.subjectRef(),
                request.modality(),
                request.position(),
                Base64.getDecoder().decode(request.probeBase64()),
                new BiometricProbeContext(request.livenessScore(), request.deviceAttestationRef(), null));
        return ResponseEntity.ok(
                new VerifyResponse(decision.result(), decision.confidence(), decision.summary()));
    }

    @PostMapping("/identify")
    public ResponseEntity<?> identify(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = IDENTIFY_REASON_HEADER, required = false) String identifyReason,
            @Valid @RequestBody IdentifyRequest request) {
        String reason = identifyReason == null ? null : identifyReason.trim().toUpperCase(Locale.ROOT);
        if (reason == null || !ALLOWED_IDENTIFY_REASONS.contains(reason)) {
            // 1:N identification is restricted (§11): enrolment / recovery / deduplication only.
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "IDENTIFY_REASON_REQUIRED",
                    "message", IDENTIFY_REASON_HEADER + " header must be one of "
                            + ALLOWED_IDENTIFY_REASONS + " — 1:N identification is restricted"));
        }
        List<IdentificationCandidate> candidates = templateService.identify(
                tenantId,
                request.modality(),
                Base64.getDecoder().decode(request.probeBase64()),
                reason,
                new BiometricProbeContext(request.livenessScore(), request.deviceAttestationRef(), null));
        List<IdentifyResponse.IdentifyCandidate> dtoCandidates = candidates.stream()
                .map(c -> new IdentifyResponse.IdentifyCandidate(c.subjectRef(), c.confidence(), c.summary()))
                .toList();
        // NEVER an automatic merge — candidates go to human adjudication.
        return ResponseEntity.ok(new IdentifyResponse(
                reason, IdentifyResponse.DECISION_CANDIDATES_FOR_ADJUDICATION, dtoCandidates));
    }
}
