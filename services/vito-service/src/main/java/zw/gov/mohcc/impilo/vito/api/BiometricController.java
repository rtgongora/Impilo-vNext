package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.vito.core.BiometricModality;
import zw.gov.mohcc.impilo.vito.core.biometric.BiometricService;
import zw.gov.mohcc.impilo.vito.persistence.entity.BiometricTemplateEntity;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Biometric Template API — INTERNAL mode only.
 * External consumers cannot access biometric data.
 */
@RestController
@RequestMapping("/v1/biometric")
public class BiometricController {

    private final BiometricService biometricService;

    public BiometricController(BiometricService biometricService) {
        this.biometricService = biometricService;
    }

    @PostMapping("/enroll")
    public ResponseEntity<ApiResponse<Long>> enroll(@RequestBody EnrollRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        if (ctx.mode() == AccessMode.EXTERNAL) {
            return ResponseEntity.status(403).body(
                    ApiResponse.error("FORBIDDEN", "Biometric enrollment is internal only", 403,
                            ctx.correlationId().toString()));
        }

        byte[] templateData = Base64.getDecoder().decode(request.templateDataBase64());

        BiometricTemplateEntity template = biometricService.enroll(
                ctx.tenantId(), request.healthId(), request.modality(), request.position(),
                templateData, request.qualityScore(), request.captureDevice(), ctx.actorId());

        return ResponseEntity.status(201).body(
                ApiResponse.ok(template.getId(), ctx.correlationId().toString()));
    }

    @GetMapping("/{healthId}")
    public ResponseEntity<ApiResponse<List<BiometricTemplateEntity>>> getTemplates(
            @PathVariable UUID healthId) {
        TrustContext ctx = TrustContextHolder.require();

        if (ctx.mode() == AccessMode.EXTERNAL) {
            return ResponseEntity.status(403).body(
                    ApiResponse.error("FORBIDDEN", "Biometric data is internal only", 403,
                            ctx.correlationId().toString()));
        }

        List<BiometricTemplateEntity> templates = biometricService.getActiveTemplates(
                ctx.tenantId(), healthId);

        return ResponseEntity.ok(ApiResponse.ok(templates, ctx.correlationId().toString()));
    }

    record EnrollRequest(UUID healthId, BiometricModality modality, String position,
                         String templateDataBase64, Integer qualityScore, String captureDevice) {}
}
