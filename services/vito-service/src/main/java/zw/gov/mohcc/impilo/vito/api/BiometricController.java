package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.vito.core.BiometricModality;
import zw.gov.mohcc.impilo.vito.core.biometric.BiometricOperationsService;
import zw.gov.mohcc.impilo.vito.core.biometric.BiometricService;
import zw.gov.mohcc.impilo.vito.core.biometric.engine.BiometricProbeContext;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricExceptionRecordEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricIdentificationEventEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricProfileEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricTemplateEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricVerificationEventEntity;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Biometric API — INTERNAL mode only. Templates are never returned in full fidelity from list APIs.
 */
@RestController
@RequestMapping("/v1/biometric")
public class BiometricController {

    private final BiometricService biometricService;
    private final BiometricOperationsService biometricOperationsService;

    public BiometricController(
            BiometricService biometricService, BiometricOperationsService biometricOperationsService) {
        this.biometricService = biometricService;
        this.biometricOperationsService = biometricOperationsService;
    }

    @PostMapping("/enroll")
    public ResponseEntity<ApiResponse<Long>> enroll(@RequestBody GovernedEnrollRequest request) {
        requireInternal();

        String workflow = request.workflowType() == null || request.workflowType().isBlank()
                ? "CLIENT_REGISTRATION"
                : request.workflowType();
        String context = request.contextType() == null || request.contextType().isBlank()
                ? "FACILITY"
                : request.contextType();

        byte[] templateData = Base64.getDecoder().decode(request.templateDataBase64());

        BiometricTemplateEntity template = biometricOperationsService.enrollGoverned(
                request.healthId(),
                request.modality(),
                request.position(),
                templateData,
                request.qualityScore(),
                request.captureDevice(),
                workflow,
                context,
                request.consentRef(),
                request.authorizationRef(),
                request.vendorEngine(),
                request.livenessSupported() != null && request.livenessSupported());

        return ResponseEntity.status(201).body(
                ApiResponse.ok(template.getId(), TrustContextHolder.require().correlationId().toString()));
    }

    @GetMapping("/{healthId}/templates")
    public ResponseEntity<ApiResponse<List<BiometricTemplateSummary>>> listTemplateSummaries(@PathVariable UUID healthId) {
        requireInternal();
        TrustContext ctx = TrustContextHolder.require();
        List<BiometricTemplateSummary> rows = biometricService.getActiveTemplates(ctx.tenantId(), healthId).stream()
                .map(BiometricTemplateSummary::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(rows, ctx.correlationId().toString()));
    }

    @GetMapping("/{healthId}")
    @Deprecated
    public ResponseEntity<ApiResponse<List<BiometricTemplateSummary>>> getTemplatesLegacy(@PathVariable UUID healthId) {
        return listTemplateSummaries(healthId);
    }

    @GetMapping("/{healthId}/profile")
    public ResponseEntity<ApiResponse<BiometricProfileEntity>> getProfile(@PathVariable UUID healthId) {
        requireInternal();
        TrustContext ctx = TrustContextHolder.require();
        return biometricOperationsService
                .findProfile(ctx.tenantId(), healthId)
                .map(p -> ResponseEntity.ok(ApiResponse.ok(p, ctx.correlationId().toString())))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND", "No biometric profile for client", 404,
                                ctx.correlationId().toString())));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<BiometricVerificationEventEntity>> verify(@RequestBody VerifyRequest request) {
        requireInternal();
        byte[] probe = Base64.getDecoder().decode(request.templateDataBase64());
        String intent = request.biometricIntent() == null || request.biometricIntent().isBlank()
                ? "VERIFY"
                : request.biometricIntent();
        BiometricProbeContext probeCtx = new BiometricProbeContext(
                request.livenessScore(), request.deviceAttestationRef(), request.engineHint());
        BiometricVerificationEventEntity ev = biometricOperationsService.verifyGoverned(
                request.healthId(),
                request.modality(),
                probe,
                request.workflowType(),
                request.contextType(),
                intent,
                probeCtx);
        return ResponseEntity.ok(ApiResponse.ok(ev, TrustContextHolder.require().correlationId().toString()));
    }

    @PostMapping("/identify")
    public ResponseEntity<ApiResponse<BiometricIdentificationEventEntity>> identify(
            @RequestBody IdentifyRequest request) {
        requireInternal();
        byte[] probe = Base64.getDecoder().decode(request.templateDataBase64());
        BiometricIdentificationEventEntity ev = biometricOperationsService.identifyGoverned(
                request.modality(), probe, request.workflowType(), request.contextType());
        return ResponseEntity.ok(ApiResponse.ok(ev, TrustContextHolder.require().correlationId().toString()));
    }

    @PostMapping("/dedup-assist")
    public ResponseEntity<ApiResponse<BiometricIdentificationEventEntity>> dedupAssist(
            @RequestBody IdentifyRequest request) {
        requireInternal();
        byte[] probe = Base64.getDecoder().decode(request.templateDataBase64());
        BiometricIdentificationEventEntity ev = biometricOperationsService.assistDeduplicationGoverned(
                request.modality(), probe, request.workflowType(), request.contextType());
        return ResponseEntity.ok(ApiResponse.ok(ev, TrustContextHolder.require().correlationId().toString()));
    }

    @PostMapping("/exception")
    public ResponseEntity<ApiResponse<Long>> exception(@RequestBody ExceptionRequest request) {
        requireInternal();
        BiometricExceptionRecordEntity saved = biometricOperationsService.recordException(
                request.healthId(),
                request.workflowType(),
                request.exceptionType(),
                request.reason(),
                request.fallbackUsed() != null && request.fallbackUsed());
        return ResponseEntity.status(201)
                .body(ApiResponse.ok(saved.getId(), TrustContextHolder.require().correlationId().toString()));
    }

    private static void requireInternal() {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() == AccessMode.EXTERNAL) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Biometric operations are internal only");
        }
    }

    public record GovernedEnrollRequest(
            UUID healthId,
            BiometricModality modality,
            String position,
            String templateDataBase64,
            Integer qualityScore,
            String captureDevice,
            String workflowType,
            String contextType,
            String consentRef,
            String authorizationRef,
            String vendorEngine,
            Boolean livenessSupported) {}

    public record VerifyRequest(
            UUID healthId,
            BiometricModality modality,
            String templateDataBase64,
            String workflowType,
            String contextType,
            String biometricIntent,
            Double livenessScore,
            String deviceAttestationRef,
            String engineHint) {}

    public record IdentifyRequest(
            BiometricModality modality,
            String templateDataBase64,
            String workflowType,
            String contextType) {}

    public record ExceptionRequest(
            UUID healthId,
            String workflowType,
            String exceptionType,
            String reason,
            Boolean fallbackUsed) {}
}
