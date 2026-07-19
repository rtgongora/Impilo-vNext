package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Steward decision on an identity-proofing / recovery review (Identity Journey Doctrine §6,
 * Wave G — the officer-witnessed path). The officer approves or rejects; on approval the
 * Trust Core records the witnessed outcome to the proofing ledger (VITO) <b>and</b> raises
 * the reviewed citizen's assurance level (IA) so an in-person / video verification actually
 * changes access decisions — the symmetric completion of the self-service proofing bridge.
 *
 * <p>The reviewed citizen's account is resolved by VITO (the record's bound account) and
 * flows here only within the trust core; the officer never needs to know it.</p>
 */
@RestController
@RequestMapping("/internal/v1/identity/steward/proofing/reviews")
public class StewardProofingReviewController {

    private static final Logger log = LoggerFactory.getLogger(StewardProofingReviewController.class);

    private final VitoServiceClient vito;
    private final IdentityAssuranceServiceClient assurance;

    public StewardProofingReviewController(VitoServiceClient vito, IdentityAssuranceServiceClient assurance) {
        this.vito = vito;
        this.assurance = assurance;
    }

    public record DecisionRequest(String tenantId, String reviewer, String decision,
                                  String outcome, String notes) {}

    @PostMapping("/{reviewId}/decision")
    public ResponseEntity<Map<String, Object>> decide(
            @PathVariable String reviewId,
            @RequestBody DecisionRequest req) {

        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", req.tenantId());
        body.put("reviewer", req.reviewer());
        body.put("decision", req.decision());
        body.put("outcome", req.outcome());
        body.put("notes", req.notes());

        JsonNode r = vito.proofingReviewDecision(reviewId, body);
        String status = r == null ? "UNKNOWN" : r.path("status").asText("UNKNOWN");
        out.put("reviewId", reviewId);
        out.put("status", status);
        out.put("grantedOutcome", r == null ? null : r.path("grantedOutcome").asText(null));

        if ("APPROVED".equals(status)) {
            int loa = r.path("assuranceLevel").asInt(0);
            String boundAccount = r.path("boundAccountRef").asText(null);
            String outcome = r.path("grantedOutcome").asText("REVIEW_APPROVED");
            raiseAssurance(loa, boundAccount, "PROOFING_REVIEW:" + outcome);
            out.put("assuranceRaised", loa >= 2 && boundAccount != null);
        }
        return ResponseEntity.ok(out);
    }

    private void raiseAssurance(int loa, String boundAccount, String provenance) {
        if (boundAccount == null || boundAccount.isBlank()) {
            // No account linked yet — the assurance applies when the person claims/links (audited on the ledger meanwhile).
            log.info("proofing review approved with no bound account; assurance deferred to account link");
            return;
        }
        String level = switch (loa) {
            case 4 -> "LOA4";
            case 3 -> "LOA3";
            case 2 -> "LOA2";
            default -> null;
        };
        if (level == null) {
            return;
        }
        try {
            assurance.recordProofingOutcome(level, provenance, boundAccount);
        } catch (Exception e) {
            log.warn("steward proofing assurance-raise not applied ({}): {}", provenance, e.getMessage());
        }
    }
}
