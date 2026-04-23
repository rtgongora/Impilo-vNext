package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.core.biometric.ProviderBiometricModality;
import zw.gov.mohcc.impilo.varapi.core.biometric.ProviderBiometricService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderBiometricProfileEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderBiometricTemplateEntity;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/provider-biometric")
public class ProviderBiometricController {

    private final ProviderBiometricService providerBiometricService;

    public ProviderBiometricController(ProviderBiometricService providerBiometricService) {
        this.providerBiometricService = providerBiometricService;
    }

    @PostMapping("/{providerPublicId}/enroll")
    public ResponseEntity<ApiResponse<Long>> enroll(
            @PathVariable String providerPublicId, @RequestBody EnrollBody body) {
        byte[] data = Base64.getDecoder().decode(body.templateDataBase64());
        ProviderBiometricTemplateEntity saved = providerBiometricService.enroll(
                providerPublicId,
                body.modality(),
                body.position(),
                data,
                body.qualityScore(),
                body.captureDevice(),
                body.workflowType(),
                body.contextType(),
                body.vendorEngine(),
                body.livenessSupported() != null && body.livenessSupported());
        return ResponseEntity.status(201)
                .body(ApiResponse.ok(saved.getId(), TrustContextHolder.require().correlationId().toString()));
    }

    @GetMapping("/{providerPublicId}/templates")
    public ResponseEntity<ApiResponse<List<ProviderBiometricTemplateSummary>>> templates(
            @PathVariable String providerPublicId) {
        List<ProviderBiometricTemplateSummary> rows =
                providerBiometricService.listActive(providerPublicId).stream()
                        .map(ProviderBiometricTemplateSummary::from)
                        .toList();
        return ResponseEntity.ok(
                ApiResponse.ok(rows, TrustContextHolder.require().correlationId().toString()));
    }

    @GetMapping("/{providerPublicId}/profile")
    public ResponseEntity<ApiResponse<ProviderBiometricProfileEntity>> profile(
            @PathVariable String providerPublicId) {
        return providerBiometricService
                .findProfile(providerPublicId)
                .map(p -> ResponseEntity.ok(
                        ApiResponse.ok(p, TrustContextHolder.require().correlationId().toString())))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ApiResponse.error(
                                "NOT_FOUND",
                                "No biometric profile",
                                404,
                                TrustContextHolder.require().correlationId().toString())));
    }

    @PostMapping("/{providerPublicId}/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verify(
            @PathVariable String providerPublicId, @RequestBody VerifyBody body) {
        byte[] probe = Base64.getDecoder().decode(body.templateDataBase64());
        Map<String, Object> res = providerBiometricService.verify(
                providerPublicId,
                body.modality(),
                probe,
                body.workflowType(),
                body.contextType(),
                body.biometricIntent());
        return ResponseEntity.ok(ApiResponse.ok(res, TrustContextHolder.require().correlationId().toString()));
    }

    public record EnrollBody(
            ProviderBiometricModality modality,
            String position,
            String templateDataBase64,
            Integer qualityScore,
            String captureDevice,
            String workflowType,
            String contextType,
            String vendorEngine,
            Boolean livenessSupported) {}

    public record VerifyBody(
            ProviderBiometricModality modality,
            String templateDataBase64,
            String workflowType,
            String contextType,
            String biometricIntent) {}
}
