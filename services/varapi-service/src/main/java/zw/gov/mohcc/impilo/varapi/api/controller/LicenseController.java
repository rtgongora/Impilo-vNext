package zw.gov.mohcc.impilo.varapi.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.api.dto.IssueLicenseRequest;
import zw.gov.mohcc.impilo.varapi.api.dto.LicenseResponse;
import zw.gov.mohcc.impilo.varapi.core.LicenseService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;

import java.util.List;

/**
 * Internal REST API for managing provider licenses within the VARAPI Provider Registry.
 *
 * Licenses represent the formal authorization from a regulatory council
 * for a provider to practice. This controller manages the full license
 * lifecycle: issuance, renewal, suspension, and revocation.
 *
 * All endpoints require a valid trust context provided by TSHEPO ext_authz.
 */
@RestController
@RequestMapping("/v1/internal/providers/{providerPublicId}/licenses")
public class LicenseController {

    private static final Logger log = LoggerFactory.getLogger(LicenseController.class);

    private final LicenseService licenseService;

    public LicenseController(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LicenseResponse>> issueLicense(
            @PathVariable String providerPublicId,
            @Valid @RequestBody IssueLicenseRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Issuing license [providerPublicId={}, type={}] correlationId={}",
                providerPublicId, request.licenseType(), ctx.correlationId());

        LicenseEntity entity = licenseService.issueLicense(providerPublicId, request);
        LicenseResponse response = toLicenseResponse(entity);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/{licenseId}/renew")
    public ResponseEntity<ApiResponse<LicenseResponse>> renewLicense(
            @PathVariable String providerPublicId,
            @PathVariable Long licenseId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Renewing license [providerPublicId={}, licenseId={}] correlationId={}",
                providerPublicId, licenseId, ctx.correlationId());

        LicenseEntity entity = licenseService.renewLicense(providerPublicId, licenseId);
        LicenseResponse response = toLicenseResponse(entity);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/{licenseId}/suspend")
    public ResponseEntity<ApiResponse<LicenseResponse>> suspendLicense(
            @PathVariable String providerPublicId,
            @PathVariable Long licenseId,
            @RequestParam String reason) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Suspending license [providerPublicId={}, licenseId={}, reason={}] correlationId={}",
                providerPublicId, licenseId, reason, ctx.correlationId());

        LicenseEntity entity = licenseService.suspendLicense(providerPublicId, licenseId, reason);
        LicenseResponse response = toLicenseResponse(entity);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/{licenseId}/revoke")
    public ResponseEntity<ApiResponse<LicenseResponse>> revokeLicense(
            @PathVariable String providerPublicId,
            @PathVariable Long licenseId,
            @RequestParam String reason) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Revoking license [providerPublicId={}, licenseId={}, reason={}] correlationId={}",
                providerPublicId, licenseId, reason, ctx.correlationId());

        LicenseEntity entity = licenseService.revokeLicense(providerPublicId, licenseId, reason);
        LicenseResponse response = toLicenseResponse(entity);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LicenseResponse>>> getLicenseHistory(
            @PathVariable String providerPublicId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching license history [providerPublicId={}] correlationId={}",
                providerPublicId, ctx.correlationId());

        List<LicenseEntity> entities = licenseService.getLicenseHistory(providerPublicId);
        List<LicenseResponse> responses = entities.stream()
                .map(this::toLicenseResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(responses, ctx.correlationId().toString()));
    }

    // ---- Mapper: Entity → Response DTO ----

    private LicenseResponse toLicenseResponse(LicenseEntity entity) {
        return new LicenseResponse(
                entity.getId(),
                entity.getProvider().getProviderPublicId(),
                entity.getCouncil() != null ? entity.getCouncil().getId() : null,
                entity.getCouncil() != null ? entity.getCouncil().getName() : null,
                entity.getLicenseType(),
                entity.getLicenseNumber(),
                entity.getStatus(),
                entity.getValidFrom(),
                entity.getValidTo(),
                entity.getConditions(),
                entity.getIssuedBy(),
                entity.getIssuedAt(),
                entity.getSuspendedAt(),
                entity.getSuspensionReason(),
                entity.getRevokedAt(),
                entity.getRevocationReason(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
