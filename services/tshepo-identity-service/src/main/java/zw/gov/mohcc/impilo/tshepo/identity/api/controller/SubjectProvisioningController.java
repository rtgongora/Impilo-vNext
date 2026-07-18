package zw.gov.mohcc.impilo.tshepo.identity.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tshepo.identity.core.SubjectProvisioningService;

import java.util.Map;
import java.util.UUID;

/**
 * Clinical-plane provisional subject minting (Identity Contract §7.2 step 4).
 *
 * <p>{@code POST /v1/identity/subjects/provisional} is what clinical services and the
 * BFF call for an unknown patient (trauma, mass-casualty). It orchestrates the VITO
 * identity mint internally and returns {@code {cpid, status}} — the Health ID never
 * transits to the caller. VITO's {@code /internal/v1/identities/provisional} is
 * restricted to this service.</p>
 */
@RestController
@RequestMapping("/v1/identity/subjects")
public class SubjectProvisioningController {

    private final SubjectProvisioningService provisioningService;

    public SubjectProvisioningController(SubjectProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    /**
     * Request body (all optional — an unknown patient may have none of it):
     * <pre>{ "estimated_sex": "M", "estimated_date_of_birth": null, "descriptor": "..." }</pre>
     * Response: <pre>{ "cpid": "…", "status": "PROVISIONAL" }</pre>
     */
    @PostMapping("/provisional")
    public ResponseEntity<ApiResponse<Map<String, Object>>> provision(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Map<String, Object> result = provisioningService.provisionSubject(
                UUID.fromString(tenantId), actorId, idempotencyKey, body);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result, null));
    }
}
