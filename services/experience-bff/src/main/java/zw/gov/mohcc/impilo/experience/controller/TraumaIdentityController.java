package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.Map;

/**
 * Trauma unknown-patient identity (WU5) — thin passthrough to VITO for the two identity
 * operations the trauma web reconcile surface needs:
 *
 * <ul>
 *   <li>{@code POST /internal/v1/trauma/identity/provisional} — mint a PROVISIONAL CPID
 *       for an unidentified trauma patient (stamped as {@code patient_cpid} across the
 *       episode). Forwards to VITO {@code POST /internal/v1/identities/provisional}.</li>
 *   <li>{@code POST /internal/v1/trauma/identity/merge} — reconcile the provisional
 *       identity into a confirmed survivor. Forwards to VITO
 *       {@code POST /internal/v1/patients/merge}, which runs {@code MergeService}
 *       (reversible {@code MergeHistory} + emits {@code vito.merge.executed}); that event
 *       is what fans out the repoint to every trauma anchor. The web MUST use this
 *       merge, never the per-service internal repoint hooks.</li>
 * </ul>
 *
 * <p>Composition/orchestration only — no persistence, no business logic. The Envoy
 * ext_authz → TSHEPO gate still governs these calls; the proxy only forwards the trust
 * context (via the RestTemplate interceptor).</p>
 */
@RestController
@RequestMapping("/internal/v1/trauma/identity")
public class TraumaIdentityController {

    private static final Logger log = LoggerFactory.getLogger(TraumaIdentityController.class);

    private final VitoServiceClient vito;

    public TraumaIdentityController(VitoServiceClient vito) {
        this.vito = vito;
    }

    private static JsonNode requirePayload(JsonNode node, String op) {
        if (node == null || node.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, op + ": upstream returned no payload");
        }
        return node;
    }

    private static ResponseStatusException upstreamFailure(String op, Exception cause) {
        log.warn("{} failed: {}", op, cause.getMessage());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, op + " failed", cause);
    }

    @PostMapping("/provisional")
    public ResponseEntity<Map<String, Object>> mintProvisional(@RequestBody(required = false) Map<String, Object> body) {
        try {
            JsonNode minted = requirePayload(vito.mintProvisionalIdentity(body != null ? body : Map.of()), "VITO mintProvisional");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", minted));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("VITO mintProvisional", e);
        }
    }

    @PostMapping("/merge")
    public ResponseEntity<Map<String, Object>> merge(@RequestBody Map<String, Object> body) {
        try {
            JsonNode merged = requirePayload(vito.mergePatients(body), "VITO mergePatients");
            return ResponseEntity.ok(Map.of("data", merged));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("VITO mergePatients", e);
        }
    }
}
