package zw.gov.mohcc.impilo.vito.api.v11;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.vito.core.IdentityService;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * V1.1 provisional-identity endpoint for the emergency/trauma pipeline.
 *
 * <p>{@code POST /internal/v1/identities/provisional} mints a VITO identity "from birth" for an
 * unknown patient (mass-casualty, unconscious trauma arrival). The returned {@code health_id}
 * is the IDENTITY-PLANE anchor — it is <b>not</b> a CPID and must never be stamped as
 * {@code patient_cpid} (Identity Contract §7.2). The only permitted caller is
 * <b>tshepo-identity</b>'s {@code SubjectProvisioningService}, which maps the new Health ID
 * to an independent CPID and hands clinical callers {@code {cpid, status}} only. When the
 * patient is later identified, reconciliation is a reversible VITO {@code MergeService.merge}
 * (see {@code POST /internal/v1/patients/merge}); the SubjectTranslationRelay converts the
 * merge into {@code impilo.identity.subject.merged.v1}, which repoints every downstream
 * clinical link (see {@code IdentityRepointHandler} in shared-kernel-java).</p>
 *
 * <p>The minted identity is {@code PROVISIONAL} / {@code UNVERIFIED} / IAL 0 — exactly the state
 * {@link IdentityService#register} produces — so it flows through the existing verify/merge
 * lifecycle with no special casing.</p>
 *
 * <p>Protected by the standard filter chain (all applied automatically):</p>
 * <ul>
 *   <li>{@code V1_1HeaderFilter} — mandatory v1.1 headers.</li>
 *   <li>{@code IdempotencyFilter} — Idempotency-Key required for POST on {@code /internal/v1/**},
 *       so a retried mint does not create a second identity.</li>
 *   <li>Caller allowlist — {@code X-Service-Id} must match
 *       {@code vito.identity.provisional-mint-caller} (default {@code tshepo-identity}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/identities")
public class V11IdentitiesController {

    private final IdentityService identityService;
    private final String allowedCaller;

    public V11IdentitiesController(
            IdentityService identityService,
            @org.springframework.beans.factory.annotation.Value(
                    "${vito.identity.provisional-mint-caller:tshepo-identity}") String allowedCaller) {
        this.identityService = identityService;
        this.allowedCaller = allowedCaller;
    }

    /**
     * Mint a provisional identity for an unknown/unidentified patient.
     *
     * <p>Request body (all optional — an unknown patient may have none of it):</p>
     * <pre>{
     *   "estimated_sex": "M",              // best-effort; "UNKNOWN" if not assessable
     *   "estimated_date_of_birth": null,   // ISO date if age-estimated, else null
     *   "descriptor": "Adult male, RTC, unconscious"  // scene descriptor, for later matching
     * }</pre>
     *
     * <p>Response:</p>
     * <pre>{
     *   "health_id": "…",   // CPID — stamp as patient_cpid across the trauma episode
     *   "crid": "…",
     *   "status": "PROVISIONAL"
     * }</pre>
     */
    @PostMapping("/provisional")
    public ResponseEntity<Map<String, Object>> mintProvisional(
            @RequestBody(required = false) ProvisionalRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
            @RequestHeader(value = "X-Service-Id", required = false) String callerServiceId) {

        // Only the trust core's translator may receive a raw health_id (Identity
        // Contract §7.2). Clinical services use tshepo-identity's
        // POST /v1/identity/subjects/provisional, which returns a CPID.
        if (callerServiceId == null || !callerServiceId.startsWith(allowedCaller)) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "PROVISIONAL_MINT_RESTRICTED",
                    "message", "This identity-plane endpoint is restricted to " + allowedCaller
                            + "; clinical callers must use tshepo-identity /v1/identity/subjects/provisional"));
        }

        ProvisionalRequest req = request != null ? request : new ProvisionalRequest();
        UUID tenantUuid = UUID.fromString(tenantId);

        // An unknown patient carries no confirmed name; use a stable UNKNOWN placeholder and
        // preserve the scene descriptor separately for later biometric/demographic matching.
        String sex = (req.estimatedSex != null && !req.estimatedSex.isBlank())
                ? req.estimatedSex : "UNKNOWN";

        IdentityService.ClientRegistrationResult result = identityService.register(
                tenantUuid,
                "UNKNOWN",
                "UNKNOWN",
                req.estimatedDateOfBirth,
                sex,
                actorId != null ? actorId : "emergency-intake");

        ClientEntity client = result.client();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("health_id", client.getHealthId().toString());
        response.put("crid", client.getCrid().toString());
        response.put("status", client.getStatus().name());
        response.put("did", result.did());
        if (req.descriptor != null && !req.descriptor.isBlank()) {
            response.put("descriptor", req.descriptor);
        }

        return ResponseEntity.status(201).body(response);
    }

    /** Request body for a provisional-identity mint. Snake_case JSON binding; all fields optional. */
    public static class ProvisionalRequest {
        @JsonProperty("estimated_sex")
        public String estimatedSex;

        @JsonProperty("estimated_date_of_birth")
        public String estimatedDateOfBirth;

        @JsonProperty("descriptor")
        public String descriptor;
    }
}
