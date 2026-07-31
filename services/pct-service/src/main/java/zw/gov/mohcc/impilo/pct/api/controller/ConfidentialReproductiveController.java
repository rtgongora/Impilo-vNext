package zw.gov.mohcc.impilo.pct.api.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.pct.core.clinical.ContraceptiveEpisodeService;
import zw.gov.mohcc.impilo.pct.core.clinical.DeliveryRecordService;
import zw.gov.mohcc.impilo.pct.core.clinical.FertilityEpisodeService;
import zw.gov.mohcc.impilo.pct.core.clinical.PostnatalContactService;
import zw.gov.mohcc.impilo.pct.core.clinical.PreconceptionService;
import zw.gov.mohcc.impilo.pct.core.clinical.PregnancyEpisodeService;
import zw.gov.mohcc.impilo.pct.core.clinical.PregnancyLossRecordService;
import zw.gov.mohcc.impilo.pct.core.clinical.ReproductiveIntentionService;
import zw.gov.mohcc.impilo.pct.core.clinical.TerminationService;
import zw.gov.mohcc.impilo.pct.persistence.entity.ContraceptiveEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.DeliveryRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.FertilityEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PostnatalContactEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PreconceptionPlanEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyLossRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReproductiveIntentionEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.TopAuthorisationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.TopProcedureEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * <h2>Stamped and unstamped reproductive records, on one lane</h2>
 * <p>Losses, postnatal contacts, TOP procedures, TOP authorisations, contraceptive episodes and
 * pregnancy episodes — the six tables pct {@code V437} added the stamp to — share this lane with
 * reproductive intention, preconception plans, fertility episodes and delivery records, which carry
 * no stamp columns yet. Those four are mounted here so tshepo-authz classifies the path before the
 * decision-table extension adds stamping hooks; their views deliberately omit stamp fields until then.
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
    private final ContraceptiveEpisodeService contraception;
    private final PregnancyEpisodeService pregnancies;
    private final ReproductiveIntentionService intentions;
    private final PreconceptionService preconception;
    private final FertilityEpisodeService fertility;
    private final DeliveryRecordService deliveries;

    public ConfidentialReproductiveController(PregnancyLossRecordService losses,
                                              PostnatalContactService postnatalContacts,
                                              TerminationService terminations,
                                              ContraceptiveEpisodeService contraception,
                                              PregnancyEpisodeService pregnancies,
                                              ReproductiveIntentionService intentions,
                                              PreconceptionService preconception,
                                              FertilityEpisodeService fertility,
                                              DeliveryRecordService deliveries) {
        this.losses = losses;
        this.postnatalContacts = postnatalContacts;
        this.terminations = terminations;
        this.contraception = contraception;
        this.pregnancies = pregnancies;
        this.intentions = intentions;
        this.preconception = preconception;
        this.fertility = fertility;
        this.deliveries = deliveries;
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

    /**
     * Termination authorisations for a subject, including requests that were declined.
     *
     * <p>Separate from the procedures above because it answers a different question — what was asked
     * for and what the state decided — and because it exists for terminations that never happened.
     */
    @GetMapping("/top-authorisations/{subjectCpid}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> topAuthorisations(
            @PathVariable String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> body =
                terminations.authorisationsForSubject(ctx.tenantId(), subjectCpid).stream()
                        .map(ConfidentialReproductiveController::authorisationView)
                        .toList();
        return ResponseEntity.ok(ApiResponse.ok(body, String.valueOf(ctx.correlationId())));
    }

    /**
     * The methods she is currently using, with coverage resolved as at a date.
     *
     * <p>Serves {@code coverage} rather than the bare episodes because a contraceptive episode
     * without its computed coverage is close to useless clinically and actively misleading: a stored
     * episode with no end date reads as protection that may have lapsed months ago. The service
     * filters before it computes, so a withheld episode contributes no gap to the list.
     *
     * <p>{@code asOf} is optional and defaults to today in the service. A client asking "was she
     * covered when she conceived" passes a past date; the arithmetic is the same either way.
     */
    @GetMapping("/contraception/{subjectCpid}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> contraception(
            @PathVariable String subjectCpid,
            @RequestParam(name = "asOf", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> body = contraception.coverage(ctx.tenantId(), subjectCpid, asOf).stream()
                .map(ConfidentialReproductiveController::coverageView)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(body, String.valueOf(ctx.correlationId())));
    }

    /** Every method she has ever been recorded on, current and stopped. */
    @GetMapping("/contraception/{subjectCpid}/history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> contraceptionHistory(
            @PathVariable String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> body = contraception.history(ctx.tenantId(), subjectCpid).stream()
                .map(ConfidentialReproductiveController::contraceptiveView)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(body, String.valueOf(ctx.correlationId())));
    }

    /**
     * Her obstetric history: every pregnancy episode, ongoing and ended.
     *
     * <p>Goes through the guarded {@code history}, not {@code pregnant} — the boolean accessor is
     * unguarded by a recorded decision (stamping doc §8) and must never back an endpoint.
     */
    @GetMapping("/pregnancy-episodes/{subjectCpid}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> pregnancyEpisodes(
            @PathVariable String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> body = pregnancies.history(ctx.tenantId(), subjectCpid).stream()
                .map(ConfidentialReproductiveController::pregnancyView)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(body, String.valueOf(ctx.correlationId())));
    }

    /**
     * Her current pregnancy, or nothing.
     *
     * <p>Returns a 200 with a null body rather than a 404 when there is no visible episode. The two
     * cases this lane must not distinguish are "she is not pregnant" and "she is pregnant and you may
     * not know", and a 404 on one of them names the other.
     */
    @GetMapping("/pregnancy-episodes/{subjectCpid}/current")
    public ResponseEntity<ApiResponse<Map<String, Object>>> currentPregnancy(
            @PathVariable String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> body = pregnancies.currentPregnancy(ctx.tenantId(), subjectCpid)
                .map(ConfidentialReproductiveController::pregnancyView)
                .orElse(null);
        return ResponseEntity.ok(ApiResponse.ok(body, String.valueOf(ctx.correlationId())));
    }

    /**
     * Her current stated reproductive intention, or nothing.
     *
     * <p>No stamp columns on {@code pct_reproductive_intentions} yet — stamping waits for the
     * decision-table extension documented in the SRH confidentiality pack.
     */
    @GetMapping("/reproductive-intentions/{subjectCpid}/current")
    public ResponseEntity<ApiResponse<Map<String, Object>>> currentIntention(
            @PathVariable String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> body = intentions.current(ctx.tenantId(), subjectCpid)
                .map(ConfidentialReproductiveController::intentionView)
                .orElse(null);
        return ResponseEntity.ok(ApiResponse.ok(body, String.valueOf(ctx.correlationId())));
    }

    /** Every reproductive intention she has ever stated. */
    @GetMapping("/reproductive-intentions/{subjectCpid}/history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> intentionHistory(
            @PathVariable String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> body = intentions.history(ctx.tenantId(), subjectCpid).stream()
                .map(ConfidentialReproductiveController::intentionView)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(body, String.valueOf(ctx.correlationId())));
    }

    /** Her active preconception plan, or nothing. */
    @GetMapping("/preconception-plans/{subjectCpid}/active")
    public ResponseEntity<ApiResponse<Map<String, Object>>> activePreconceptionPlan(
            @PathVariable String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> body = preconception.active(ctx.tenantId(), subjectCpid)
                .map(ConfidentialReproductiveController::preconceptionView)
                .orElse(null);
        return ResponseEntity.ok(ApiResponse.ok(body, String.valueOf(ctx.correlationId())));
    }

    /** Every preconception plan recorded for her. */
    @GetMapping("/preconception-plans/{subjectCpid}/history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> preconceptionHistory(
            @PathVariable String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> body = preconception.history(ctx.tenantId(), subjectCpid).stream()
                .map(ConfidentialReproductiveController::preconceptionView)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(body, String.valueOf(ctx.correlationId())));
    }

    /** Her open fertility episode, or nothing — {@code open()} mapped to current. */
    @GetMapping("/fertility-episodes/{subjectCpid}/current")
    public ResponseEntity<ApiResponse<Map<String, Object>>> currentFertilityEpisode(
            @PathVariable String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> body = fertility.open(ctx.tenantId(), subjectCpid)
                .map(ConfidentialReproductiveController::fertilityView)
                .orElse(null);
        return ResponseEntity.ok(ApiResponse.ok(body, String.valueOf(ctx.correlationId())));
    }

    /** Every fertility episode recorded for her. */
    @GetMapping("/fertility-episodes/{subjectCpid}/history")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> fertilityHistory(
            @PathVariable String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> body = fertility.history(ctx.tenantId(), subjectCpid).stream()
                .map(ConfidentialReproductiveController::fertilityView)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(body, String.valueOf(ctx.correlationId())));
    }

    /** Every delivery recorded for a mother. */
    @GetMapping("/delivery-records/mother/{motherCpid}")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> deliveriesForMother(
            @PathVariable String motherCpid) {
        TrustContext ctx = TrustContextHolder.require();
        List<Map<String, Object>> body = deliveries.forMother(ctx.tenantId(), motherCpid).stream()
                .map(ConfidentialReproductiveController::deliveryView)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(body, String.valueOf(ctx.correlationId())));
    }

    /** The delivery for a pregnancy episode, or nothing. */
    @GetMapping("/delivery-records/pregnancy/{pregnancyEpisodeId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deliveryForPregnancy(
            @PathVariable UUID pregnancyEpisodeId) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> body = deliveries.forPregnancy(ctx.tenantId(), pregnancyEpisodeId)
                .map(ConfidentialReproductiveController::deliveryView)
                .orElse(null);
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

    private static Map<String, Object> authorisationView(TopAuthorisationEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("authorisation_id", str(e.getAuthorisationId()));
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("pregnancy_episode_id", str(e.getPregnancyEpisodeId()));
        m.put("status", e.getStatus());
        m.put("legal_ground", e.getLegalGround());
        m.put("gestation_weeks_at_request", e.getGestationWeeksAtRequest());
        m.put("gestational_limit_weeks", e.getGestationalLimitWeeks());
        m.put("certifying_authority", e.getCertifyingAuthority());
        m.put("certified_on", str(e.getCertifiedOn()));
        // Authorisation is the state's decision; consent is hers. Reported separately because an
        // authorised termination she did not consent to is a safeguarding event, not a formality —
        // collapsing the two into one "approved" flag is what makes that invisible.
        m.put("consent_given", e.getConsentGiven());
        m.put("consent_recorded_on", str(e.getConsentRecordedOn()));
        m.put("objection_referral_ref", e.getObjectionReferralRef());
        stamp(m, e.getSensitivityClass(), e.getConfidentialityCategory(), e.getConfidentialityBasis());
        return m;
    }

    private static Map<String, Object> contraceptiveView(ContraceptiveEpisodeEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("contraceptive_episode_id", str(e.getContraceptiveEpisodeId()));
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("method_code", e.getMethodCode());
        m.put("method_class", e.getMethodClass());
        m.put("status", e.getStatus());
        m.put("started_on", str(e.getStartedOn()));
        m.put("expected_end_on", str(e.getExpectedEndOn()));
        m.put("ended_on", str(e.getEndedOn()));
        m.put("discontinuation_reason", e.getDiscontinuationReason());
        m.put("eligibility_category", e.getEligibilityCategory());
        m.put("eligibility_overridden", e.getEligibilityOverridden());
        m.put("deferred_insertion_owed", e.getDeferredInsertionOwed());
        stamp(m, e.getSensitivityClass(), e.getConfidentialityCategory(), e.getConfidentialityBasis());
        return m;
    }

    /**
     * A method with its computed coverage.
     *
     * <p>{@code coverage_unknown_why} is carried verbatim and never dropped. UNKNOWN with no reason
     * renders as a shrug and gets ignored, and the one case that matters — a woman months past her
     * injection — then looks exactly like a data-entry gap.
     */
    private static Map<String, Object> coverageView(ContraceptiveEpisodeService.MethodCoverage c) {
        Map<String, Object> m = contraceptiveView(c.episode());
        m.put("coverage_status", c.status() == null ? null : c.status().name());
        m.put("days_remaining", c.daysRemaining());
        m.put("last_administered_on", str(c.lastAdministeredOn()));
        m.put("next_due_on", str(c.nextDueOn()));
        m.put("coverage_unknown_why", c.coverageUnknownWhy());
        m.put("content_version", c.contentVersion());
        return m;
    }

    /**
     * A pregnancy episode.
     *
     * <p>The stored estimated delivery date and its basis travel together, and no gestational age is
     * computed here. Dating is stamped once by {@code PregnancyEpisodeService} and read everywhere
     * else; a view that re-derived gestation would be the second component able to compute it, and
     * the two would eventually disagree about how pregnant a woman is in front of a clinician.
     */
    private static Map<String, Object> pregnancyView(PregnancyEpisodeEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pregnancy_episode_id", str(e.getPregnancyEpisodeId()));
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("status", e.getStatus());
        m.put("confirmation_basis", e.getConfirmationBasis());
        m.put("confirmed_on", str(e.getConfirmedOn()));
        m.put("pregnancy_start_date", str(e.getPregnancyStartDate()));
        m.put("estimated_delivery_date", str(e.getEstimatedDeliveryDate()));
        m.put("dating_method", e.getDatingMethod());
        m.put("dating_confidence", e.getDatingConfidence());
        m.put("dating_plus_minus_days", e.getDatingPlusMinusDays());
        m.put("dating_revision_count", e.getDatingRevisionCount());
        m.put("plurality", e.getPlurality());
        m.put("plurality_basis", e.getPluralityBasis());
        // NOT_ASSESSED is not a negative, in either direction: an unassessed risk status is not "low
        // risk", and an unassessed intention is not "unintended". Passed through as recorded so a
        // client cannot render an assessment nobody made.
        m.put("risk_status", e.getRiskStatus());
        m.put("intended_pregnancy", e.getIntendedPregnancy());
        m.put("planned_place_of_birth", e.getPlannedPlaceOfBirth());
        m.put("outcome", e.getOutcome());
        m.put("ended_on", str(e.getEndedOn()));
        stamp(m, e.getSensitivityClass(), e.getConfidentialityCategory(), e.getConfidentialityBasis());
        return m;
    }

    /**
     * The stamp travels with every record on this lane. A client that shows a confidentiality badge
     * needs the category, and one debugging a withheld record needs the basis — including
     * {@code AGE_UNRESOLVED} and {@code POLICY_UNAVAILABLE}, which say the stamp degraded openly
     * rather than that the record is ordinary.
     */
    private static Map<String, Object> intentionView(ReproductiveIntentionEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("intention_id", str(e.getIntentionId()));
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("intention", e.getIntention());
        m.put("timeframe_months", e.getTimeframeMonths());
        m.put("desired_family_size", e.getDesiredFamilySize());
        m.put("partner_intention", e.getPartnerIntention());
        m.put("fertility_concern", e.getFertilityConcern());
        m.put("contraception_desired", e.getContraceptionDesired());
        m.put("unmet_need", e.getUnmetNeed());
        m.put("notes", e.getNotes());
        m.put("status", e.getStatus());
        m.put("recorded_at", str(e.getRecordedAt()));
        m.put("recorded_by", e.getRecordedBy());
        m.put("client_offline_id", e.getClientOfflineId());
        return m;
    }

    private static Map<String, Object> preconceptionView(PreconceptionPlanEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("preconception_plan_id", str(e.getPreconceptionPlanId()));
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("status", e.getStatus());
        m.put("opened_on", str(e.getOpenedOn()));
        m.put("closed_on", str(e.getClosedOn()));
        m.put("closure_reason", e.getClosureReason());
        m.put("folic_acid_started_on", str(e.getFolicAcidStartedOn()));
        m.put("folic_acid_dose_mg", e.getFolicAcidDoseMg());
        m.put("folic_acid_high_dose_reason", e.getFolicAcidHighDoseReason());
        m.put("teratogenic_medication_found", e.getTeratogenicMedicationFound());
        m.put("teratogenic_medication_action", e.getTeratogenicMedicationAction());
        m.put("diabetes_control_optimised", e.getDiabetesControlOptimised());
        m.put("hypertension_control_optimised", e.getHypertensionControlOptimised());
        m.put("rubella_immunity_status", e.getRubellaImmunityStatus());
        m.put("hiv_status_known", e.getHivStatusKnown());
        m.put("hiv_art_optimised", e.getHivArtOptimised());
        m.put("recorded_at", str(e.getRecordedAt()));
        m.put("recorded_by", e.getRecordedBy());
        m.put("client_offline_id", e.getClientOfflineId());
        return m;
    }

    private static Map<String, Object> fertilityView(FertilityEpisodeEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fertility_episode_id", str(e.getFertilityEpisodeId()));
        m.put("subject_cpid", e.getSubjectCpid());
        m.put("partner_cpid", e.getPartnerCpid());
        m.put("partner_linkage_consent", e.getPartnerLinkageConsent());
        m.put("status", e.getStatus());
        m.put("opened_on", str(e.getOpenedOn()));
        m.put("closed_on", str(e.getClosedOn()));
        m.put("outcome", e.getOutcome());
        m.put("months_trying", e.getMonthsTrying());
        m.put("investigation_threshold_met", e.getInvestigationThresholdMet());
        m.put("primary_or_secondary", e.getPrimaryOrSecondary());
        m.put("female_factor_assessed", e.getFemaleFactorAssessed());
        m.put("female_factor_findings", e.getFemaleFactorFindings());
        m.put("male_factor_assessed", e.getMaleFactorAssessed());
        m.put("male_factor_findings", e.getMaleFactorFindings());
        m.put("recorded_at", str(e.getRecordedAt()));
        m.put("recorded_by", e.getRecordedBy());
        m.put("client_offline_id", e.getClientOfflineId());
        return m;
    }

    private static Map<String, Object> deliveryView(DeliveryRecordEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("delivery_record_id", str(e.getDeliveryRecordId()));
        m.put("mother_cpid", e.getMotherCpid());
        m.put("pregnancy_episode_id", str(e.getPregnancyEpisodeId()));
        m.put("status", e.getStatus());
        m.put("delivered_at", str(e.getDeliveredAt()));
        m.put("labour_onset", e.getLabourOnset());
        m.put("delivery_mode", e.getDeliveryMode());
        m.put("theatre_episode_ref", e.getTheatreEpisodeRef());
        m.put("place_of_birth", e.getPlaceOfBirth());
        m.put("pph_suspected", e.getPphSuspected());
        m.put("estimated_blood_loss_ml", e.getEstimatedBloodLossMl());
        m.put("perineal_outcome", e.getPerinealOutcome());
        m.put("babies_delivered", e.getBabiesDelivered());
        m.put("recorded_at", str(e.getRecordedAt()));
        m.put("recorded_by", e.getRecordedBy());
        m.put("client_offline_id", e.getClientOfflineId());
        return m;
    }

    private static void stamp(Map<String, Object> m, String sensitivityClass, String category, String basis) {
        m.put("sensitivity_class", sensitivityClass);
        m.put("confidentiality_category", category);
        m.put("confidentiality_basis", basis);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
