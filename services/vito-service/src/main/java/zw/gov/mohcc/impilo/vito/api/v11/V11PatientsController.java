package zw.gov.mohcc.impilo.vito.api.v11;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.vito.core.merge.MergeService;
import zw.gov.mohcc.impilo.vito.persistence.entity.MergeHistoryEntity;

import java.util.*;

/**
 * V1.1 patient merge endpoint.
 *
 * POST /internal/v1/patients/merge
 *
 * Protected by (all applied automatically via filter chain):
 *   - V1_1HeaderFilter (mandatory v1.1 headers: X-Tenant-ID, X-Pod-ID, X-Request-ID, X-Correlation-ID)
 *   - IdempotencyFilter (Idempotency-Key required for POST on /internal/v1/**)
 *   - FederationAuthorityGuard (X-Pod-ID must be "national" — enforced inside MergeService)
 */
@RestController
@RequestMapping("/internal/v1/patients")
public class V11PatientsController {

    private final MergeService mergeService;

    public V11PatientsController(MergeService mergeService) {
        this.mergeService = mergeService;
    }

    /**
     * Merge one or more patient records into a survivor.
     *
     * Request body:
     * {
     *   "survivor_crid": "CRID-1",
     *   "merged_crids": ["CRID-2","CRID-3"],
     *   "reason": "DUPLICATE_REGISTRATION"
     * }
     *
     * Response:
     * {
     *   "merge_id": "...",
     *   "survivor_crid": "...",
     *   "alias_map": [{"from":"CRID-2","to":"CRID-1"}, {"from":"CRID-3","to":"CRID-1"}]
     * }
     */
    @PostMapping("/merge")
    public ResponseEntity<Map<String, Object>> merge(
            @RequestBody MergeRequest request,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader("X-Request-ID") String requestId,
            @RequestHeader("X-Correlation-ID") String correlationId,
            @RequestHeader(value = "X-Actor-Id", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType) {

        UUID tenantUuid = UUID.fromString(tenantId);
        UUID survivorCrid = UUID.fromString(request.survivorCrid);

        List<Map<String, String>> aliasMap = new ArrayList<>();
        Long lastMergeId = null;

        for (String mergedCridStr : request.mergedCrids) {
            UUID mergedCrid = UUID.fromString(mergedCridStr);

            MergeHistoryEntity history = mergeService.merge(
                    tenantUuid,
                    survivorCrid,
                    mergedCrid,
                    null, // no linked dedup case
                    request.reason,
                    null, // no field decisions
                    actorId != null ? actorId : "system",
                    actorType != null ? actorType : "SERVICE"
            );

            lastMergeId = history.getId();
            aliasMap.add(Map.of(
                    "from", mergedCridStr,
                    "to", request.survivorCrid
            ));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("merge_id", lastMergeId != null ? lastMergeId.toString() : null);
        response.put("survivor_crid", request.survivorCrid);
        response.put("alias_map", aliasMap);

        return ResponseEntity.ok(response);
    }

    /**
     * Request body for patient merge. Uses @JsonProperty for snake_case JSON binding.
     */
    public static class MergeRequest {
        @JsonProperty("survivor_crid")
        public String survivorCrid;

        @JsonProperty("merged_crids")
        public List<String> mergedCrids;

        @JsonProperty("reason")
        public String reason;
    }
}
