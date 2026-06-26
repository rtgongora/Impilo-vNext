package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.core.cadre.CadreDecision;
import zw.gov.mohcc.impilo.pct.core.cadre.CadreDecisionRequest;
import zw.gov.mohcc.impilo.pct.core.cadre.CadreEngineService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Map;

/**
 * Cadre Engine decision API (shared read-model C9).
 *
 * <p>Resolves the permitted-workflow set + adaptive cockpit spine + escalation hints for a
 * role/cadre/scope/visit-type/acuity/context/access-state tuple. The Encounter Cockpit renders strictly from
 * {@link CadreDecision#cockpitSpine()} — disabled actions are never rendered as live buttons.</p>
 *
 * <p>Authorization is enforced upstream at the Envoy ext_authz layer (TSHEPO). The required policy rule is
 * {@code CADRE-ACTION} (track P / WS-OPA {@code impilo.authz}); this controller does not author policy.
 * Every resolution is audited (see {@link CadreEngineService}).</p>
 */
@RestController
@RequestMapping("/v1/cadre")
public class CadreController {

    private final CadreEngineService cadreEngineService;

    public CadreController(CadreEngineService cadreEngineService) {
        this.cadreEngineService = cadreEngineService;
    }

    @PostMapping("/decision")
    public ResponseEntity<ApiResponse<CadreDecision>> resolve(@RequestBody DecisionBody body) {
        // TODO(policy CADRE-ACTION): execution-time authz for cadre-gated actions calls the existing
        //   Tshepo ext_authz path; the rule itself is authored in track P / WS-OPA impilo.authz.
        CadreDecisionRequest req = new CadreDecisionRequest(
                body.role(), body.cadre(), body.scope(), body.visitType(),
                body.acuity(), body.context(), body.accessState());
        CadreDecision decision = cadreEngineService.resolveAndAudit(req, body.journeyId(), body.encounterId());
        return ResponseEntity.ok(ApiResponse.ok(decision, TrustContextHolder.require().correlationId().toString()));
    }

    /** Inputs map field-for-field to the C9 {@code CadreDecisionRequest}; journey/encounter are audit correlations. */
    public record DecisionBody(
            String role,
            String cadre,
            String scope,
            String visitType,
            Integer acuity,
            String context,
            String accessState,
            String journeyId,
            String encounterId) {

        public DecisionBody {
            // Defensive defaults are applied in the engine; this record only carries the raw request.
        }
    }

    /** Lightweight introspection: the static cadre→workflow contract without auditing (no PHI, no side effects). */
    @GetMapping("/contract")
    public ResponseEntity<ApiResponse<Map<String, Object>>> contract() {
        Map<String, Object> doc = Map.of(
                "contract", "C9",
                "description", "Cadre Engine resolves permitted workflows + adaptive cockpit spine + escalation.",
                "cockpitTabs", java.util.List.of(
                        "OVERVIEW", "ASSESSMENT", "PROBLEMS", "ORDERS_RESULTS",
                        "CARE", "CONSULTS_REFERRALS", "NOTES", "VISIT_OUTCOME"),
                "note", "Cockpit renders strictly from cockpitSpine; disabled actions are not live buttons.");
        return ResponseEntity.ok(ApiResponse.ok(doc, TrustContextHolder.require().correlationId().toString()));
    }
}
