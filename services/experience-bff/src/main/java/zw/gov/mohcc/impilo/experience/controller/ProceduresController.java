package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.ProceduresServiceClient;

/**
 * Experience-BFF facade for procedures-service — the Clinical Procedures Pipeline platform
 * layer. Composition/orchestration only; procedures-service is engine-not-store, so this
 * controller has no write endpoints and forwards every response verbatim.
 *
 * <p>Every route stays under {@code /internal/v1/procedures/**}, which envoy already routes
 * generically to the experience_bff cluster — no envoy change was needed to make this
 * reachable (see the Wave P-R commit history). tshepo-authz gates each route at the ext_authz
 * edge by its own resource_type; see V300 in tshepo-authz-service for the policy rows and the
 * route-shape reasoning ({@code catalogue-detail} takes its code as a query parameter rather
 * than a path variable, because a free-text procedure code as the final path segment defeats
 * the PDP's UUID-only resource-type derivation).</p>
 *
 * <p><b>Failure never renders as empty.</b> A downstream 5xx or connection failure from
 * {@link ProceduresServiceClient} propagates as a real exception, caught by
 * {@code BffGlobalExceptionHandler} and returned as a genuine non-2xx status with a structured
 * error body — never masked into a 200 with an empty or null payload. That guarantee is
 * necessary but not sufficient: the frontend hook consuming this controller must not collapse
 * that non-2xx status back into an empty array with a {@code ?? []} fallback, which is exactly
 * how a live BFF 502 once rendered as "no conditions" for every patient on the adult problem
 * list. See the procedures React Query hooks for the client-side half of this guarantee.</p>
 */
@RestController
@RequestMapping("/internal/v1/procedures")
public class ProceduresController {

    private final ProceduresServiceClient procedures;

    public ProceduresController(ProceduresServiceClient procedures) {
        this.procedures = procedures;
    }

    @GetMapping("/catalogue")
    public ResponseEntity<String> searchCatalogue(
            @RequestParam(required = false) String specialty,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q) {
        return procedures.searchCatalogue(specialty, category, q);
    }

    /**
     * Catalogue detail via query parameter, deliberately — see the class docs and V300's own
     * commentary on why a REST path variable would break ext_authz's resource-type derivation
     * for a free-text procedure code.
     */
    @GetMapping("/catalogue-detail")
    public ResponseEntity<String> catalogueDetail(@RequestParam String code) {
        return procedures.catalogueDetail(code);
    }

    @PostMapping(value = "/appropriateness/evaluate", consumes = "application/json")
    public ResponseEntity<String> evaluateAppropriateness(@RequestBody String requestBody) {
        return procedures.evaluateAppropriateness(requestBody);
    }

    @GetMapping("/competence")
    public ResponseEntity<String> resolveCompetence(
            @RequestParam String providerId,
            @RequestParam String definitionCode) {
        return procedures.resolveCompetence(providerId, definitionCode);
    }

    // ── Wave P-R2 — P7 safety-pause/sedation and P9 recovery/aftercare routes.
    // See V301 in tshepo-authz-service for the policy rows. Query-param code shape throughout,
    // for the same PDP-derivation reason as catalogue-detail above. ──

    @GetMapping("/safety-pause-templates")
    public ResponseEntity<String> safetyPauseTemplate(@RequestParam String code) {
        return procedures.safetyPauseTemplate(code);
    }

    @GetMapping("/sedation-levels")
    public ResponseEntity<String> sedationLevels() {
        return procedures.sedationLevels();
    }

    @GetMapping("/sedation-level-detail")
    public ResponseEntity<String> sedationLevel(@RequestParam String code) {
        return procedures.sedationLevel(code);
    }

    @GetMapping("/recovery-settings")
    public ResponseEntity<String> recoverySettings() {
        return procedures.recoverySettings();
    }

    @GetMapping("/recovery-setting-detail")
    public ResponseEntity<String> recoverySetting(@RequestParam String code) {
        return procedures.recoverySetting(code);
    }

    @GetMapping("/aftercare-templates")
    public ResponseEntity<String> aftercareTemplate(@RequestParam String code) {
        return procedures.aftercareTemplate(code);
    }

    // ── Wave SB-3 — P14 analytics indicator catalogue routes. See V302 in tshepo-authz-service
    // for the policy rows. Query-param code shape, same PDP-derivation reason as above. ──

    @GetMapping("/analytics/indicators")
    public ResponseEntity<String> analyticsIndicators() {
        return procedures.analyticsIndicators();
    }

    @GetMapping("/analytics/indicator")
    public ResponseEntity<String> analyticsIndicator(@RequestParam String code) {
        return procedures.analyticsIndicator(code);
    }

    // ── Wave W4 — P10 Clavien-Dindo grades and complication profiles. Catalogue risk
    // content only — see V306 in tshepo-authz-service. Not surgery pathway grading. ──

    @GetMapping("/clavien-dindo-grades")
    public ResponseEntity<String> clavienDindoGrades() {
        return procedures.clavienDindoGrades();
    }

    @GetMapping("/complication-profiles")
    public ResponseEntity<String> complicationProfile(@RequestParam String code) {
        return procedures.complicationProfile(code);
    }
}
