package zw.gov.mohcc.impilo.tuso.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.tuso.core.FacilityCredentialService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCredentialEntity;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;

/**
 * Facility QR credentials (FJ QR, D-L7). Issuance/revocation is a governed
 * registry act (steward gate); the PUBLIC scan lane lives on the
 * /v1/public/facilities family (permitAll + Envoy trust-header stripping) and
 * returns a policy-filtered projection with a uniform NOT_RECOGNISED miss.
 */
@RestController
public class FacilityCredentialController {

    /**
     * Issuing or revoking a facility credential.
     *
     * <p>Was {@code Set.of("SYSTEM", "REGISTRY_ADMIN", "NATIONAL_ADMIN", "DATA_STEWARD")}; the last
     * three are role names no client emits.</p>
     */
    private static final ActorTypeGuard.Duty ISSUE_FACILITY_CREDENTIAL = new ActorTypeGuard.Duty(
            "issuing a facility credential",
            ActorTypeGuard.BACK_OFFICE_WRITERS,
            Set.of("REGISTRY_ADMIN", "NATIONAL_ADMIN", "DATA_STEWARD"));

    private final FacilityCredentialService credentialService;

    public FacilityCredentialController(FacilityCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping("/v1/internal/facilities/{facilityUuid}/credentials")
    public ResponseEntity<FacilityCredentialService.IssuedCredential> issue(
            @PathVariable("facilityUuid") UUID facilityUuid, HttpServletRequest http) {
        requireIssuer(http);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(credentialService.issue(facilityUuid));
    }

    @PostMapping("/v1/internal/facility-credentials/{verificationCode}/revoke")
    public ResponseEntity<Map<String, Object>> revoke(
            @PathVariable("verificationCode") String verificationCode,
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest http) {
        requireIssuer(http);
        FacilityCredentialEntity credential = credentialService.revoke(
                verificationCode, body != null ? body.get("reason") : null);
        return ResponseEntity.ok(Map.of(
                "verificationCode", credential.getVerificationCode(),
                "status", credential.getStatus()));
    }

    /** Public scan — QR content or short code in the body, never the URL/logs. */
    @PostMapping("/v1/public/facilities/credential-scan")
    public ResponseEntity<FacilityCredentialService.CredentialVerification> verifyScan(
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(credentialService.verifyScan(
                body != null ? body.getOrDefault("qr", body.get("code")) : null));
    }

    private static void requireIssuer(HttpServletRequest http) {
        String actorType = http.getHeader("X-Actor-Type");
        ActorTypeGuard.require(actorType, http.getHeader("X-Actor-ID"), ISSUE_FACILITY_CREDENTIAL);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> onConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
