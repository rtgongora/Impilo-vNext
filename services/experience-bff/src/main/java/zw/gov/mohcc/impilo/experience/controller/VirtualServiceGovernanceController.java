package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.telemedicine.VirtualHospitalCompositionService;
import zw.gov.mohcc.impilo.experience.telemedicine.VirtualPoolDutyService;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Virtual-service GOVERNANCE composition for the experience shell's
 * registry-admin panel: seam edits, queue-def edits, fail-closed
 * activate/suspend, change history, and the honest runtime read-backs
 * (PCT materialisation + vashandi coverage).
 *
 * <p>Pure composition: TUSO is the SoR and enforces authz server-side (the UI
 * display-gates only). Upstream governance failures (e.g. the 422 activation
 * precondition) are passed through with their status and message — never
 * softened.</p>
 */
@RestController
@RequestMapping("/internal/v1/telemedicine/virtual-services")
public class VirtualServiceGovernanceController {

    private static final Logger log = LoggerFactory.getLogger(VirtualServiceGovernanceController.class);

    private final TusoServiceClient tusoServiceClient;
    private final PctServiceClient pctServiceClient;
    private final VirtualPoolDutyService dutyService;

    public VirtualServiceGovernanceController(TusoServiceClient tusoServiceClient,
                                              PctServiceClient pctServiceClient,
                                              VirtualPoolDutyService dutyService) {
        this.tusoServiceClient = tusoServiceClient;
        this.pctServiceClient = pctServiceClient;
        this.dutyService = dutyService;
    }

    /** Governed partial update (seam active/note + queue-def name/SLA/rules edits). */
    @PatchMapping("/{vsUid}")
    public ResponseEntity<Object> patch(@PathVariable String vsUid, @RequestBody Map<String, Object> body) {
        return proxy(() -> tusoServiceClient.updateVirtualService(vsUid, body));
    }

    /** POST alias for clients that cannot PATCH. */
    @PostMapping("/{vsUid}/update")
    public ResponseEntity<Object> patchViaPost(@PathVariable String vsUid, @RequestBody Map<String, Object> body) {
        return proxy(() -> tusoServiceClient.updateVirtualService(vsUid, body));
    }

    /** Add a routing seam (pool key). */
    @PostMapping("/{vsUid}/routing-seams")
    public ResponseEntity<Object> addSeam(@PathVariable String vsUid, @RequestBody Map<String, Object> body) {
        return proxy(() -> tusoServiceClient.addVirtualServiceRoutingSeam(vsUid, body));
    }

    /** Request activation (TUSO fail-closed: 422 without seam+queue-def). */
    @PostMapping("/{vsUid}/activate")
    public ResponseEntity<Object> activate(@PathVariable String vsUid) {
        return proxy(() -> tusoServiceClient.activateVirtualService(vsUid));
    }

    /** Suspend the virtual service. */
    @PostMapping("/{vsUid}/suspend")
    public ResponseEntity<Object> suspend(@PathVariable String vsUid,
                                          @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null || body.get("reason") == null ? null : body.get("reason").toString();
        return proxy(() -> tusoServiceClient.suspendVirtualService(vsUid, reason));
    }

    /** Governed-change history (TUSO virtual_service_history read-back). */
    @GetMapping("/{vsUid}/history")
    public ResponseEntity<Object> history(@PathVariable String vsUid) {
        return proxy(() -> tusoServiceClient.getVirtualServiceHistory(vsUid));
    }

    /**
     * Runtime read-backs for the governance panel: PCT pool materialisation
     * state + vashandi duty coverage. Each leg degrades independently and
     * honestly (absent/UNKNOWN), never fails the panel.
     */
    @GetMapping("/{vsUid}/readbacks")
    public ResponseEntity<Object> readbacks(@PathVariable String vsUid) {
        JsonNode vs;
        try {
            vs = tusoServiceClient.getVirtualService(vsUid);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getResponseBodyAsString()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "TUSO unreachable: " + e.getMessage()));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("vsUid", vsUid);
        out.put("lifecycleStatus", vs == null ? null : vs.path("lifecycleStatus").asText(null));

        String poolId = null;
        if (vs != null && vs.get("routingSeams") != null && vs.get("routingSeams").isArray()) {
            for (JsonNode seam : vs.get("routingSeams")) {
                if (seam.path("active").asBoolean(false)) {
                    poolId = seam.path("targetRef").asText(null);
                    break;
                }
            }
        }
        out.put("primaryPoolId", poolId);

        if (poolId != null) {
            try {
                out.put("materialization", pctServiceClient.getVirtualPoolStats(poolId));
            } catch (Exception e) {
                log.warn("PCT materialisation read-back unavailable for {}: {}", vsUid, e.getMessage());
                out.put("materializationDegraded", true);
            }
            out.put("duty", dutyService.dutySnapshot(poolId));
        }
        return ResponseEntity.ok(out);
    }

    /** On-demand PCT reconcile for the rig / governance panel refresh. */
    @PostMapping("/reconcile-pools")
    public ResponseEntity<Object> reconcilePools() {
        return proxy(pctServiceClient::reconcileVirtualPools);
    }

    private ResponseEntity<Object> proxy(java.util.function.Supplier<JsonNode> call) {
        try {
            JsonNode result = call.get();
            return ResponseEntity.ok(result == null ? Map.of() : result);
        } catch (HttpStatusCodeException e) {
            // Pass governance refusals (422 preconditions, 404, 409) through honestly.
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Upstream unreachable: " + e.getMessage()));
        }
    }
}
