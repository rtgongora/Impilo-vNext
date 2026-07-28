package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.pct.core.clinical.PostnatalContactService;
import zw.gov.mohcc.impilo.pct.core.clinical.PregnancyLossRecordService;
import zw.gov.mohcc.impilo.pct.core.clinical.TerminationService;
import zw.gov.mohcc.impilo.pct.persistence.entity.PostnatalContactEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyLossRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.TopProcedureEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The confidential reproductive-health disclosure lane.
 *
 * <h2>Why this path, and why it is not negotiable</h2>
 * <p>tshepo-authz classifies confidentiality from the REQUEST PATH:
 * {@code ResourceSensitivityClassifier} matches the lane markers {@code confidential},
 * {@code safeguarding} and {@code protected-disclosure}, and every rule seeded by tshepo-authz
 * {@code V048} is pinned to {@code path_contains: "/confidential/"}. A reproductive route mounted
 * anywhere else receives NO {@code confidentialCategories} from the PDP — and
 * {@code SpeciallyProtectedVisibilityGuard} fails closed. After the governance flip that combination
 * would withhold every stamped record from every requester, <em>including the midwife who wrote it</em>,
 * while the service stayed green and the tests passed.
 *
 * <p>{@code scripts/guard/check-confidential-lane-routing.sh} makes that a build failure rather than
 * a ward silently losing its own records. This controller is the first consumer of that rule.
 *
 * <h2>A withheld record is answered exactly like one that does not exist</h2>
 * <p>Reads go through {@code ConfidentialRecordGuard} inside the services, which returns an absent
 * row rather than throwing. There is deliberately no 403 anywhere on this path: a 403 distinguishable
 * from a 404 tells a guardian that the confidential record IS there, which is most of what
 * confidentiality was protecting. An empty list here means "nothing you may see", and the caller
 * cannot tell that from "nothing exists".
 *
 * <h2>Currently inert, by design</h2>
 * <p>The PDP runs in SHADOW and no record carries the protected class yet
 * ({@code pct.confidentiality.stamp-class} defaults false), so today this lane returns the same rows
 * an ordinary route would. That is the intended pre-flip state — see
 * {@code docs/clinical-governance/rmnp/srh-confidentiality-stamping.md} for the ordered flip list.
 * The endpoint exists now so the routing is correct BEFORE anything depends on it.
 */
@RestController
@RequestMapping("/v1/confidential/reproductive")
public class ConfidentialReproductiveController {

    private final PregnancyLossRecordService losses;
    private final PostnatalContactService postnatalContacts;
    private final TerminationService terminations;

    public ConfidentialReproductiveController(PregnancyLossRecordService losses,
                                              PostnatalContactService postnatalContacts,
                                              TerminationService terminations) {
        this.losses = losses;
        this.postnatalContacts = postnatalContacts;
        this.terminations = terminations;
    }

    /** Pregnancy losses for a mother. Mother-anchored: a loss mints no person. */
    @GetMapping("/losses/{motherCpid}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> losses(@PathVariable String motherCpid) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> body = losses.forMother(ctx.tenantId(), motherCpid).stream()
                .map(ConfidentialReproductiveController::lossView)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(body, String.valueOf(ctx.correlationId())));
    }

    /** Postnatal contacts for a mother. The newborn's postnatal care is the paediatric pack's. */
    @GetMapping("/postnatal-contacts/{motherCpid}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> postnatalContacts(
            @PathVariable String motherCpid) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> body = postnatalContacts.forMother(ctx.tenantId(), motherCpid).stream()
                .map(ConfidentialReproductiveController::postnatalView)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(body, String.valueOf(ctx.correlationId())));
    }

    /**
     * Termination procedures for a subject. Aggregate-only reporting applies to EVENTS, not to a
     * clinician reading the record of the woman in front of them — no TOP identifier leaves the
     * service on an outbox path, and {@code check-top-no-record-level-emit.sh} enforces that.
     */
    @GetMapping("/terminations/{subjectCpid}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> terminations(
            @PathVariable String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> body = terminations.proceduresForSubject(ctx.tenantId(), subjectCpid).stream()
                .map(ConfidentialReproductiveController::terminationView)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(body, String.valueOf(ctx.correlationId())));
    }

    private static Map<String, Object> lossView(PregnancyLossRecordEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("loss_record_id", str(e.getLossRecordId()));
        m.put("mother_cpid", e.getMotherCpid());
        m.put("pregnancy_episode_id", str(e.getPregnancyEpisodeId()));
        m.put("loss_type", e.getLossType());
        m.put("occurred_on", str(e.getOccurredOn()));
        stamp(m, e.getSensitivityClass(), e.getConfidentialityCategory(), e.getConfidentialityBasis());
        return m;
    }

    private static Map<String, Object> postnatalView(PostnatalContactEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("postnatal_contact_id", str(e.getPostnatalContactId()));
        m.put("mother_cpid", e.getMotherCpid());
        m.put("contact_timing", e.getContactTiming());
        m.put("contact_setting", e.getContactSetting());
        m.put("contacted_at", str(e.getContactedAt()));
        m.put("screening_complete", e.getScreeningComplete());
        // Deliberately passed through as-is, including null. NULL means NOT SCREENED and a client
        // must render it that way — never as a reassuring "no danger signs". The schema refuses to
        // let this be non-null without a completed screen (V436 chk_pnc_screening_gate); coercing it
        // to false here would undo that in the read path.
        m.put("danger_signs_present", e.getDangerSignsPresent());
        m.put("breastfeeding_status", e.getBreastfeedingStatus());
        stamp(m, e.getSensitivityClass(), e.getConfidentialityCategory(), e.getConfidentialityBasis());
        return m;
    }

    private static Map<String, Object> terminationView(TopProcedureEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("procedure_id", str(e.getProcedureId()));
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("method", e.getMethod());
        m.put("performed_on", str(e.getPerformedOn()));
        stamp(m, e.getSensitivityClass(), e.getConfidentialityCategory(), e.getConfidentialityBasis());
        return m;
    }

    /**
     * The stamp travels with every record on this lane. A client that shows a confidentiality badge
     * needs the category, and one debugging a withheld record needs the basis — including
     * {@code AGE_UNRESOLVED} and {@code POLICY_UNAVAILABLE}, which say the stamp degraded openly
     * rather than that the record is ordinary.
     */
    private static void stamp(Map<String, Object> m, String sensitivityClass, String category, String basis) {
        m.put("sensitivity_class", sensitivityClass);
        m.put("confidentiality_category", category);
        m.put("confidentiality_basis", basis);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
