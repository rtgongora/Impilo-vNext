package zw.gov.mohcc.impilo.vito.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.vito.core.IdentityService;
import zw.gov.mohcc.impilo.vito.core.ImpiloIdAliasService;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Identity Issuance & Resolution API.
 *
 * Golden Thread: ID creation → alias issuance → DID generation → Smart Card request.
 *
 * INTERNAL mode: full access (register, resolve, rotate).
 * EXTERNAL mode: resolve only (identity verification for 3rd-party consumers).
 */
@RestController
@RequestMapping("/v1/identity")
public class IdIssuanceController {

    private final IdentityService identityService;
    private final ImpiloIdAliasService aliasService;

    public IdIssuanceController(IdentityService identityService,
                                 ImpiloIdAliasService aliasService) {
        this.identityService = identityService;
        this.aliasService = aliasService;
    }

    /**
     * Register a new identity — INTERNAL only.
     * Creates a PROVISIONAL client, generates DID, issues IMPILO_ID alias.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@RequestBody RegistrationRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        if (ctx.mode() == AccessMode.EXTERNAL) {
            return ResponseEntity.status(403).body(
                    ApiResponse.error("FORBIDDEN", "External consumers cannot register identities", 403,
                            ctx.correlationId().toString()));
        }

        var result = identityService.register(
                ctx.tenantId(),
                request.givenName(),
                request.familyName(),
                request.dateOfBirth(),
                request.sex(),
                ctx.actorId());

        return ResponseEntity.status(201).body(ApiResponse.ok(
                Map.of(
                        "healthId", result.client().getHealthId(),
                        "did", result.did(),
                        "status", result.client().getStatus()
                ),
                ctx.correlationId().toString()));
    }

    /**
     * Resolve an identity by alias — both modes.
     * External consumers use this for identity verification.
     * Response deliberately limited to health_id + status (anti-enumeration).
     */
    @PostMapping("/resolve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resolve(@RequestBody ResolveRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        Optional<UUID> healthId = aliasService.resolve(
                ctx.tenantId(), request.aliasType(), request.aliasValue());

        if (healthId.isEmpty()) {
            // Indistinguishable error response — anti-enumeration per VITO security contract
            return ResponseEntity.status(404).body(
                    ApiResponse.error("NOT_FOUND", "Identity not found or invalid", 404,
                            ctx.correlationId().toString()));
        }

        return ResponseEntity.ok(ApiResponse.ok(
                Map.of("healthId", healthId.get(), "resolved", true),
                ctx.correlationId().toString()));
    }

    /**
     * Rotate an alias — INTERNAL only.
     */
    @PostMapping("/rotate")
    public ResponseEntity<ApiResponse<String>> rotateAlias(@RequestBody RotateRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        if (ctx.mode() == AccessMode.EXTERNAL) {
            return ResponseEntity.status(403).body(
                    ApiResponse.error("FORBIDDEN", "External consumers cannot rotate aliases", 403,
                            ctx.correlationId().toString()));
        }

        aliasService.rotateAlias(ctx.tenantId(), request.healthId(),
                request.aliasType(), request.newValue());

        return ResponseEntity.ok(ApiResponse.ok("Alias rotated", ctx.correlationId().toString()));
    }

    // Request records

    record RegistrationRequest(String givenName, String familyName, String dateOfBirth, String sex) {}
    record ResolveRequest(String aliasType, String aliasValue) {}
    record RotateRequest(UUID healthId, String aliasType, String newValue) {}
}
