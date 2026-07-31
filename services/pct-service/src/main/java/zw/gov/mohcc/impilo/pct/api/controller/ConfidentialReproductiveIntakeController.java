package zw.gov.mohcc.impilo.pct.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.pct.core.clinical.DeliveryRecordService;
import zw.gov.mohcc.impilo.pct.core.clinical.FertilityEpisodeService;
import zw.gov.mohcc.impilo.pct.core.clinical.PostnatalContactService;
import zw.gov.mohcc.impilo.pct.core.clinical.PreconceptionService;
import zw.gov.mohcc.impilo.pct.core.clinical.PregnancyEpisodeService;
import zw.gov.mohcc.impilo.pct.core.clinical.ReproductiveIntentionService;
import zw.gov.mohcc.impilo.pct.persistence.entity.DeliveryRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.FertilityEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PostnatalContactEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PreconceptionPlanEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PregnancyEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ReproductiveIntentionEntity;
import zw.gov.mohcc.impilo.reproductive.dating.DatingBasis;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recording into the confidential reproductive lane: pregnancy booking, postnatal contacts,
 * reproductive intention, preconception plans, fertility episodes and delivery records.
 *
 * <p>Reproductive intention, preconception, fertility and delivery tables carry no stamp columns
 * yet — stamping waits for the decision-table extension; views omit stamp fields until then.
 *
 * <h2>Why the writes are on the confidential lane too</h2>
 * <p>Same reason as the reads. tshepo-authz classifies from the request path, and V048 seeds
 * {@code confidential-lane-write-self}, {@code -write-clinician} and {@code -write-nurse} against
 * {@code path_contains: "/confidential/"} — so a reproductive write mounted elsewhere is not merely
 * unclassified, it is outside the entitlement that was written for it.
 *
 * <h2>A conflict is a 409 carrying what the client needs to reconcile, never a 500</h2>
 * <p>Both of these paths exist to serve a capture that happened somewhere with no network, and a 500
 * to an offline client is a lost record. The mobile lane's law is
 * <em>no write path may return 200 over a destination nothing reads</em>; the corollary here is that
 * no write path may return 500 over a conflict the server understands perfectly well. A duplicate
 * booking answers 409 with the existing episode id and a named reconciliation task; a replayed
 * postnatal packet answers 200 with the contact it already created, because the packet arrived twice
 * and the visit did not.
 *
 * <h2>Requests are camelCase, responses snake_case</h2>
 * <p>As everywhere in this estate. Stated here because a snake_case request record against a
 * camelCase client is a silent 400 indistinguishable from a validation failure.
 */
@RestController
@RequestMapping("/v1/confidential/reproductive")
public class ConfidentialReproductiveIntakeController {

    private static final Logger log =
            LoggerFactory.getLogger(ConfidentialReproductiveIntakeController.class);

    private final PregnancyEpisodeService pregnancies;
    private final PostnatalContactService postnatalContacts;
    private final ReproductiveIntentionService intentions;
    private final PreconceptionService preconception;
    private final FertilityEpisodeService fertility;
    private final DeliveryRecordService deliveries;

    public ConfidentialReproductiveIntakeController(PregnancyEpisodeService pregnancies,
                                                    PostnatalContactService postnatalContacts,
                                                    ReproductiveIntentionService intentions,
                                                    PreconceptionService preconception,
                                                    FertilityEpisodeService fertility,
                                                    DeliveryRecordService deliveries) {
        this.pregnancies = pregnancies;
        this.postnatalContacts = postnatalContacts;
        this.intentions = intentions;
        this.preconception = preconception;
        this.fertility = fertility;
        this.deliveries = deliveries;
    }

    // ── pregnancy booking ─────────────────────────────────────────────────────

    /**
     * A dating basis as the client sends it.
     *
     * <p>All four methods the resolver understands are accepted rather than only the last menstrual
     * period, because a woman booking after a dating scan has better information than her recall and
     * an intake that could not carry it would throw the scan away.
     */
    public record DatingBasisRequest(
            String method,
            LocalDate observedOn,
            LocalDate lastMenstrualPeriod,
            Boolean lastMenstrualPeriodCertain,
            Integer measuredGestationalAgeDays,
            LocalDate conceptionDate,
            Integer embryoAgeDaysAtTransfer,
            String sourceRef) {
    }

    /** A booking as the client sends it. */
    public record OpenPregnancyRequest(
            String subjectCpid,
            UUID facilityId,
            String journeyId,
            String encounterId,
            List<DatingBasisRequest> datingBases,
            LocalDate recordedOn,
            LocalDate confirmedOn,
            String confirmationBasis,
            Integer plurality,
            String conceptionMode,
            String clientOfflineId,
            OffsetDateTime capturedAt,
            String recordedBy) {
    }

    /**
     * Book a pregnancy.
     *
     * <p>Three outcomes, and the distinction between them is the whole point of the endpoint:
     * <ul>
     *   <li><b>201</b> — a new episode.</li>
     *   <li><b>200</b> — a replayed {@code clientOfflineId}: the packet arrived twice, the pregnancy
     *       did not. The service returns the episode it already created and the client must treat
     *       this as success, not as a second booking.</li>
     *   <li><b>409</b> — she already has an ongoing pregnancy, or one booked in the same week. This
     *       is a conflict a human must reconcile, so the response carries the existing episode id and
     *       a reconciliation task rather than only a message. A 500 here would lose the booking; a
     *       bare 409 with no id would leave the client unable to do anything about it.</li>
     * </ul>
     */
    @PostMapping("/pregnancy-episodes")
    public ResponseEntity<Map<String, Object>> openPregnancy(
            @RequestBody OpenPregnancyRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        List<DatingBasis> bases = datingBases(request.datingBases());
        if (bases.isEmpty()) {
            // Refused rather than defaulted: without a start date every gestation-scoped rule
            // downstream silently fails to apply rather than reporting that it could not.
            return unprocessable(ctx, "PREGNANCY_UNDATABLE",
                    "A pregnancy booking must carry at least one usable dating basis: a last "
                            + "menstrual period, a dating scan, an assisted-conception transfer date "
                            + "or a clinical estimate.");
        }

        var command = new PregnancyEpisodeService.OpenPregnancyCommand(
                ctx.tenantId(), request.subjectCpid(), request.facilityId(), request.journeyId(),
                request.encounterId(), bases,
                request.recordedOn() == null ? LocalDate.now() : request.recordedOn(),
                request.confirmedOn(), request.confirmationBasis(), request.plurality(),
                request.conceptionMode(), request.clientOfflineId(), request.capturedAt(),
                request.recordedBy());

        PregnancyEpisodeService.OpenOutcome outcome;
        try {
            outcome = pregnancies.openReporting(command);
        } catch (PregnancyEpisodeService.DuplicatePregnancyException conflict) {
            log.info("pregnancy.booking.conflict subject={} existing={}",
                    request.subjectCpid(), conflict.existingEpisodeId());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(envelope(ctx, conflictBody(
                    "PREGNANCY_ALREADY_BOOKED", conflict.getMessage(),
                    conflict.existingEpisodeId(),
                    "Open the existing pregnancy episode and reconcile this booking against it. If "
                            + "the two are genuinely different pregnancies, the earlier one must be "
                            + "closed with an outcome first — a woman cannot be pregnant twice at once.")));
        } catch (PregnancyEpisodeService.UndatablePregnancyException undatable) {
            return unprocessable(ctx, "PREGNANCY_UNDATABLE", undatable.getMessage());
        }

        // A replay is a 200, a new booking a 201: the client asked for something that already existed
        // and got it, which is not the same event as creating it. The service reports which happened
        // rather than the controller inferring it, because both outcomes look identical on the row.
        PregnancyEpisodeEntity episode = outcome.episode();
        boolean replayed = outcome.replayed();
        HttpStatus status = replayed ? HttpStatus.OK : HttpStatus.CREATED;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("pregnancy_episode_id", str(episode.getPregnancyEpisodeId()));
        data.put("subject_cpid", episode.getSubjectCpid());
        data.put("status", episode.getStatus());
        data.put("pregnancy_start_date", str(episode.getPregnancyStartDate()));
        data.put("estimated_delivery_date", str(episode.getEstimatedDeliveryDate()));
        data.put("dating_method", episode.getDatingMethod());
        data.put("dating_confidence", episode.getDatingConfidence());
        data.put("dating_plus_minus_days", episode.getDatingPlusMinusDays());
        data.put("client_offline_id", episode.getClientOfflineId());
        data.put("replayed", replayed);
        data.put("sensitivity_class", episode.getSensitivityClass());
        data.put("confidentiality_category", episode.getConfidentialityCategory());
        return ResponseEntity.status(status).body(envelope(ctx, data));
    }

    private static List<DatingBasis> datingBases(List<DatingBasisRequest> requested) {
        List<DatingBasis> out = new ArrayList<>();
        if (requested == null) {
            return out;
        }
        for (DatingBasisRequest r : requested) {
            if (r == null || r.method() == null) {
                continue;
            }
            DatingBasis basis = switch (r.method().toUpperCase()) {
                case "LMP" -> r.lastMenstrualPeriod() == null ? null : DatingBasis.fromLmp(
                        r.lastMenstrualPeriod(),
                        // Null certainty is UNCERTAIN, never certain. Recall a clinician did not
                        // vouch for must not be promoted to the confidence a scan would carry.
                        Boolean.TRUE.equals(r.lastMenstrualPeriodCertain()));
                case "ULTRASOUND" -> DatingBasis.fromUltrasound(
                        r.observedOn(), r.measuredGestationalAgeDays(), r.sourceRef());
                case "ASSISTED_CONCEPTION" -> DatingBasis.fromAssistedConception(
                        r.conceptionDate() == null ? r.observedOn() : r.conceptionDate(),
                        r.embryoAgeDaysAtTransfer(), r.sourceRef());
                case "CLINICAL_ESTIMATE" -> DatingBasis.clinicalEstimate(
                        r.observedOn(), r.measuredGestationalAgeDays(), r.sourceRef());
                // An unrecognised method is dropped rather than guessed at. If every basis is
                // dropped the request is refused as undatable, which is louder than defaulting one.
                default -> null;
            };
            if (basis != null) {
                out.add(basis);
            }
        }
        return out;
    }

    // ── postnatal contact ─────────────────────────────────────────────────────

    /**
     * A postnatal contact as the client sends it.
     *
     * <p>{@code screeningComplete}, {@code dangerSignsPresent} and {@code breastfeedingStatus} are
     * boxed and nullable on purpose. An unscreened contact must arrive as null and be stored as null:
     * {@code V436 chk_pnc_screening_gate} refuses a non-null danger-sign answer without a completed
     * screen, and a request record defaulting either to false would manufacture the false all-clear
     * the schema exists to refuse.
     */
    public record PostnatalContactRequest(
            String motherCpid,
            UUID pregnancyEpisodeId,
            String contactTiming,
            String contactSetting,
            Short contactNumber,
            OffsetDateTime contactedAt,
            Short daysSinceBirth,
            String attendedBy,
            String attendantCadre,
            String maternalCondition,
            String bleedingAssessment,
            String moodScreen,
            Boolean screeningComplete,
            Boolean dangerSignsPresent,
            String breastfeedingStatus,
            String breastfeedingDifficulty,
            Boolean lactationSupportGiven,
            Boolean contraceptionDiscussed,
            UUID postpartumContraceptionEpisodeId,
            Boolean referralMade,
            String referralReason,
            UUID referralFacilityId,
            UUID facilityId,
            String clientOfflineId,
            String recordedBy) {
    }

    /**
     * Record a postnatal contact — at home, in the community, virtually, or in a facility.
     *
     * <p>{@code contactSetting} is required and not defaulted. HOME, COMMUNITY and VIRTUAL are
     * first-class settings rather than a facility visit with a flag, and defaulting to FACILITY would
     * silently relabel every CHW visit as something that happened in a clinic — which is exactly the
     * contact the PNC schedule is worst at capturing.
     *
     * <p>A replayed {@code clientOfflineId} returns the existing contact with 200 rather than minting
     * a duplicate visit. A CHW who syncs twice must not double-count a woman's day-3 visit.
     */
    @PostMapping("/postnatal-contacts")
    public ResponseEntity<Map<String, Object>> recordPostnatalContact(
            @RequestBody PostnatalContactRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        if (request.contactSetting() == null || request.contactSetting().isBlank()) {
            return unprocessable(ctx, "PNC_SETTING_REQUIRED",
                    "A postnatal contact must say where it happened. HOME, COMMUNITY and VIRTUAL are "
                            + "first-class settings; defaulting to FACILITY would relabel a community "
                            + "visit as a clinic attendance.");
        }
        if (Boolean.TRUE.equals(request.referralMade())
                && (request.referralReason() == null || request.referralReason().isBlank())) {
            return unprocessable(ctx, "PNC_REFERRAL_REASON_REQUIRED",
                    "A postnatal referral must carry its reason. A woman sent onward with nothing "
                            + "said about why arrives with the receiving clinician starting from zero.");
        }

        PostnatalContactEntity contact = new PostnatalContactEntity();
        contact.setTenantId(ctx.tenantId());
        contact.setMotherCpid(request.motherCpid());
        contact.setPregnancyEpisodeId(request.pregnancyEpisodeId());
        contact.setContactTiming(request.contactTiming());
        contact.setContactSetting(request.contactSetting());
        contact.setContactNumber(request.contactNumber());
        contact.setContactedAt(request.contactedAt() == null
                ? OffsetDateTime.now() : request.contactedAt());
        contact.setDaysSinceBirth(request.daysSinceBirth());
        contact.setAttendedBy(request.attendedBy());
        contact.setAttendantCadre(request.attendantCadre());
        contact.setMaternalCondition(request.maternalCondition());
        contact.setBleedingAssessment(request.bleedingAssessment());
        contact.setMoodScreen(request.moodScreen());
        // Passed through exactly as sent, nulls included. See PostnatalContactRequest.
        contact.setScreeningComplete(request.screeningComplete());
        contact.setDangerSignsPresent(request.dangerSignsPresent());
        contact.setBreastfeedingStatus(request.breastfeedingStatus());
        contact.setBreastfeedingDifficulty(request.breastfeedingDifficulty());
        contact.setLactationSupportGiven(request.lactationSupportGiven());
        contact.setContraceptionDiscussed(request.contraceptionDiscussed());
        contact.setPostpartumContraceptionEpisodeId(request.postpartumContraceptionEpisodeId());
        contact.setReferralMade(request.referralMade());
        contact.setReferralReason(request.referralReason());
        contact.setReferralFacilityId(request.referralFacilityId());
        contact.setFacilityId(request.facilityId());
        contact.setClientOfflineId(request.clientOfflineId());
        contact.setRecordedBy(request.recordedBy());

        PostnatalContactService.RecordOutcome outcome = postnatalContacts.recordReporting(contact);
        PostnatalContactEntity saved = outcome.contact();
        boolean replayed = outcome.replayed();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("postnatal_contact_id", str(saved.getPostnatalContactId()));
        data.put("mother_cpid", saved.getMotherCpid());
        data.put("contact_timing", saved.getContactTiming());
        data.put("contact_setting", saved.getContactSetting());
        data.put("contacted_at", str(saved.getContactedAt()));
        data.put("screening_complete", saved.getScreeningComplete());
        data.put("danger_signs_present", saved.getDangerSignsPresent());
        data.put("breastfeeding_status", saved.getBreastfeedingStatus());
        data.put("referral_made", saved.getReferralMade());
        data.put("referral_reason", saved.getReferralReason());
        data.put("client_offline_id", saved.getClientOfflineId());
        data.put("replayed", replayed);
        data.put("sensitivity_class", saved.getSensitivityClass());
        data.put("confidentiality_category", saved.getConfidentialityCategory());
        return ResponseEntity.status(replayed ? HttpStatus.OK : HttpStatus.CREATED)
                .body(envelope(ctx, data));
    }

    // ── reproductive intention ──────────────────────────────────────────────────

    public record RecordIntentionRequest(
            String subjectCpid,
            UUID facilityId,
            String journeyId,
            String encounterId,
            String intention,
            Integer timeframeMonths,
            Integer desiredFamilySize,
            String partnerIntention,
            String fertilityConcern,
            String contraceptionDesired,
            String unmetNeed,
            String notes,
            String clientOfflineId,
            String recordedBy) {
    }

    @PostMapping("/reproductive-intentions")
    public ResponseEntity<Map<String, Object>> recordIntention(
            @RequestBody RecordIntentionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (request.intention() == null || request.intention().isBlank()) {
            return unprocessable(ctx, "INTENTION_REQUIRED",
                    "A reproductive intention must state what she wants — silence is not a preference.");
        }
        if (request.subjectCpid() == null || request.subjectCpid().isBlank()) {
            return unprocessable(ctx, "SUBJECT_REQUIRED", "A reproductive intention must name its subject.");
        }
        if (request.recordedBy() == null || request.recordedBy().isBlank()) {
            return unprocessable(ctx, "RECORDED_BY_REQUIRED",
                    "A reproductive intention must say who recorded it.");
        }

        OffsetDateTime before = OffsetDateTime.now().minusSeconds(2);
        var command = new ReproductiveIntentionService.RecordIntentionCommand(
                ctx.tenantId(), request.subjectCpid(), request.facilityId(), request.journeyId(),
                request.encounterId(), request.intention(), request.timeframeMonths(),
                request.desiredFamilySize(), request.partnerIntention(), request.fertilityConcern(),
                request.contraceptionDesired(), request.unmetNeed(), request.notes(),
                request.clientOfflineId(), request.recordedBy());
        ReproductiveIntentionEntity saved = intentions.record(command);
        boolean replayed = request.clientOfflineId() != null
                && saved.getCreatedAt() != null
                && saved.getCreatedAt().isBefore(before);

        Map<String, Object> data = intentionBody(saved);
        data.put("replayed", replayed);
        return ResponseEntity.status(replayed ? HttpStatus.OK : HttpStatus.CREATED)
                .body(envelope(ctx, data));
    }

    // ── preconception plan ──────────────────────────────────────────────────────

    public record OpenPreconceptionPlanRequest(
            String subjectCpid,
            UUID facilityId,
            UUID journeyId,
            String encounterRef,
            LocalDate openedOn,
            LocalDate folicAcidStartedOn,
            BigDecimal folicAcidDoseMg,
            String folicAcidHighDoseReason,
            String teratogenicMedicationFound,
            String teratogenicMedicationDetail,
            String teratogenicMedicationAction,
            String diabetesControlOptimised,
            String hypertensionControlOptimised,
            String rubellaImmunityStatus,
            String hivStatusKnown,
            String hivArtOptimised,
            String clientOfflineId,
            String recordedBy) {
    }

    public record UpdatePreconceptionPlanRequest(
            String status,
            LocalDate closedOn,
            String closureReason,
            LocalDate folicAcidStartedOn,
            BigDecimal folicAcidDoseMg,
            String folicAcidHighDoseReason,
            String teratogenicMedicationFound,
            String teratogenicMedicationDetail,
            String teratogenicMedicationAction,
            String diabetesControlOptimised,
            String hypertensionControlOptimised,
            String rubellaImmunityStatus,
            String hivStatusKnown,
            String hivArtOptimised,
            String updatedBy) {
    }

    @PostMapping("/preconception-plans")
    public ResponseEntity<Map<String, Object>> openPreconceptionPlan(
            @RequestBody OpenPreconceptionPlanRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (request.subjectCpid() == null || request.subjectCpid().isBlank()) {
            return unprocessable(ctx, "SUBJECT_REQUIRED", "A preconception plan must name its subject.");
        }

        OffsetDateTime before = OffsetDateTime.now().minusSeconds(2);
        PreconceptionPlanEntity plan = new PreconceptionPlanEntity();
        plan.setTenantId(ctx.tenantId());
        plan.setSubjectCpid(request.subjectCpid());
        plan.setFacilityId(request.facilityId());
        plan.setJourneyId(request.journeyId());
        plan.setEncounterRef(request.encounterRef());
        plan.setOpenedOn(request.openedOn() == null ? LocalDate.now() : request.openedOn());
        plan.setFolicAcidStartedOn(request.folicAcidStartedOn());
        plan.setFolicAcidDoseMg(request.folicAcidDoseMg());
        plan.setFolicAcidHighDoseReason(request.folicAcidHighDoseReason());
        plan.setTeratogenicMedicationFound(request.teratogenicMedicationFound());
        plan.setTeratogenicMedicationDetail(request.teratogenicMedicationDetail());
        plan.setTeratogenicMedicationAction(request.teratogenicMedicationAction());
        plan.setDiabetesControlOptimised(request.diabetesControlOptimised());
        plan.setHypertensionControlOptimised(request.hypertensionControlOptimised());
        plan.setRubellaImmunityStatus(request.rubellaImmunityStatus());
        plan.setHivStatusKnown(request.hivStatusKnown());
        plan.setHivArtOptimised(request.hivArtOptimised());
        plan.setClientOfflineId(request.clientOfflineId());
        plan.setRecordedBy(request.recordedBy());

        try {
            PreconceptionPlanEntity saved = preconception.open(plan);
            boolean replayed = request.clientOfflineId() != null
                    && saved.getRecordedAt() != null
                    && saved.getRecordedAt().isBefore(before);
            Map<String, Object> data = preconceptionBody(saved);
            data.put("replayed", replayed);
            return ResponseEntity.status(replayed ? HttpStatus.OK : HttpStatus.CREATED)
                    .body(envelope(ctx, data));
        } catch (IllegalArgumentException refused) {
            return unprocessable(ctx, "PRECONCEPTION_REFUSED", refused.getMessage());
        }
    }

    @PatchMapping("/preconception-plans/{planId}")
    public ResponseEntity<Map<String, Object>> updatePreconceptionPlan(
            @PathVariable UUID planId,
            @RequestBody UpdatePreconceptionPlanRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        PreconceptionPlanEntity changes = new PreconceptionPlanEntity();
        changes.setStatus(request.status());
        changes.setClosedOn(request.closedOn());
        changes.setClosureReason(request.closureReason());
        changes.setFolicAcidStartedOn(request.folicAcidStartedOn());
        changes.setFolicAcidDoseMg(request.folicAcidDoseMg());
        changes.setFolicAcidHighDoseReason(request.folicAcidHighDoseReason());
        changes.setTeratogenicMedicationFound(request.teratogenicMedicationFound());
        changes.setTeratogenicMedicationDetail(request.teratogenicMedicationDetail());
        changes.setTeratogenicMedicationAction(request.teratogenicMedicationAction());
        changes.setDiabetesControlOptimised(request.diabetesControlOptimised());
        changes.setHypertensionControlOptimised(request.hypertensionControlOptimised());
        changes.setRubellaImmunityStatus(request.rubellaImmunityStatus());
        changes.setHivStatusKnown(request.hivStatusKnown());
        changes.setHivArtOptimised(request.hivArtOptimised());

        try {
            PreconceptionPlanEntity saved = preconception.update(
                    ctx.tenantId(), planId, changes,
                    request.updatedBy() == null ? "unknown" : request.updatedBy());
            return ResponseEntity.ok(envelope(ctx, preconceptionBody(saved)));
        } catch (IllegalArgumentException notFoundOrRefused) {
            String code = notFoundOrRefused.getMessage().startsWith("No preconception plan")
                    ? "PRECONCEPTION_NOT_FOUND" : "PRECONCEPTION_REFUSED";
            if ("PRECONCEPTION_NOT_FOUND".equals(code)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(envelope(ctx, Map.of(
                        "code", code, "message", notFoundOrRefused.getMessage())));
            }
            return unprocessable(ctx, code, notFoundOrRefused.getMessage());
        }
    }

    // ── fertility episode ─────────────────────────────────────────────────────

    public record StartFertilityEpisodeRequest(
            String subjectCpid,
            String partnerCpid,
            String partnerLinkageConsent,
            LocalDate openedOn,
            Short monthsTrying,
            String investigationThresholdMet,
            String primaryOrSecondary,
            String femaleFactorAssessed,
            String femaleFactorFindings,
            String maleFactorAssessed,
            String maleFactorFindings,
            UUID facilityId,
            UUID journeyId,
            String encounterRef,
            String clientOfflineId,
            String recordedBy) {
    }

    @PostMapping("/fertility-episodes")
    public ResponseEntity<Map<String, Object>> startFertilityEpisode(
            @RequestBody StartFertilityEpisodeRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (request.subjectCpid() == null || request.subjectCpid().isBlank()) {
            return unprocessable(ctx, "SUBJECT_REQUIRED", "A fertility episode must name its subject.");
        }

        OffsetDateTime before = OffsetDateTime.now().minusSeconds(2);
        FertilityEpisodeEntity episode = new FertilityEpisodeEntity();
        episode.setTenantId(ctx.tenantId());
        episode.setSubjectCpid(request.subjectCpid());
        episode.setPartnerCpid(request.partnerCpid());
        episode.setPartnerLinkageConsent(request.partnerLinkageConsent());
        episode.setOpenedOn(request.openedOn() == null ? LocalDate.now() : request.openedOn());
        episode.setMonthsTrying(request.monthsTrying());
        episode.setInvestigationThresholdMet(request.investigationThresholdMet());
        episode.setPrimaryOrSecondary(request.primaryOrSecondary());
        episode.setFemaleFactorAssessed(request.femaleFactorAssessed());
        episode.setFemaleFactorFindings(request.femaleFactorFindings());
        episode.setMaleFactorAssessed(request.maleFactorAssessed());
        episode.setMaleFactorFindings(request.maleFactorFindings());
        episode.setFacilityId(request.facilityId());
        episode.setJourneyId(request.journeyId());
        episode.setEncounterRef(request.encounterRef());
        episode.setClientOfflineId(request.clientOfflineId());
        episode.setRecordedBy(request.recordedBy());

        try {
            FertilityEpisodeEntity saved = fertility.start(episode);
            boolean replayed = request.clientOfflineId() != null
                    && saved.getRecordedAt() != null
                    && saved.getRecordedAt().isBefore(before);
            Map<String, Object> data = fertilityBody(saved);
            data.put("replayed", replayed);
            return ResponseEntity.status(replayed ? HttpStatus.OK : HttpStatus.CREATED)
                    .body(envelope(ctx, data));
        } catch (IllegalArgumentException refused) {
            return unprocessable(ctx, "FERTILITY_REFUSED", refused.getMessage());
        }
    }

    // ── delivery record ─────────────────────────────────────────────────────────

    public record RecordDeliveryRequest(
            String motherCpid,
            UUID pregnancyEpisodeId,
            OffsetDateTime deliveredAt,
            String labourOnset,
            String deliveryMode,
            String theatreEpisodeRef,
            String placeOfBirth,
            UUID facilityId,
            String birthAttendant,
            String attendantCadre,
            Boolean amtslGiven,
            String uterotonicGiven,
            String placentaDelivery,
            Boolean placentaComplete,
            Integer estimatedBloodLossMl,
            Boolean pphSuspected,
            String perinealOutcome,
            String perinealRepair,
            String immediateMaternalCondition,
            Short babiesDelivered,
            UUID journeyId,
            String encounterRef,
            String clientOfflineId,
            String recordedBy) {
    }

    @PostMapping("/delivery-records")
    public ResponseEntity<Map<String, Object>> recordDelivery(
            @RequestBody RecordDeliveryRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (request.motherCpid() == null || request.motherCpid().isBlank()) {
            return unprocessable(ctx, "MOTHER_REQUIRED", "A delivery record must name the mother.");
        }
        if (request.deliveredAt() == null) {
            return unprocessable(ctx, "DELIVERED_AT_REQUIRED",
                    "A delivery record must say when the birth happened.");
        }

        OffsetDateTime before = OffsetDateTime.now().minusSeconds(2);
        DeliveryRecordEntity delivery = new DeliveryRecordEntity();
        delivery.setTenantId(ctx.tenantId());
        delivery.setMotherCpid(request.motherCpid());
        delivery.setPregnancyEpisodeId(request.pregnancyEpisodeId());
        delivery.setDeliveredAt(request.deliveredAt());
        delivery.setLabourOnset(request.labourOnset());
        delivery.setDeliveryMode(request.deliveryMode());
        delivery.setTheatreEpisodeRef(request.theatreEpisodeRef());
        delivery.setPlaceOfBirth(request.placeOfBirth());
        delivery.setFacilityId(request.facilityId());
        delivery.setBirthAttendant(request.birthAttendant());
        delivery.setAttendantCadre(request.attendantCadre());
        delivery.setAmtslGiven(request.amtslGiven());
        delivery.setUterotonicGiven(request.uterotonicGiven());
        delivery.setPlacentaDelivery(request.placentaDelivery());
        delivery.setPlacentaComplete(request.placentaComplete());
        delivery.setEstimatedBloodLossMl(request.estimatedBloodLossMl());
        delivery.setPphSuspected(request.pphSuspected());
        delivery.setPerinealOutcome(request.perinealOutcome());
        delivery.setPerinealRepair(request.perinealRepair());
        delivery.setImmediateMaternalCondition(request.immediateMaternalCondition());
        delivery.setBabiesDelivered(request.babiesDelivered());
        delivery.setJourneyId(request.journeyId());
        delivery.setEncounterRef(request.encounterRef());
        delivery.setClientOfflineId(request.clientOfflineId());
        delivery.setRecordedBy(request.recordedBy());

        try {
            DeliveryRecordEntity saved = deliveries.record(delivery);
            boolean replayed = request.clientOfflineId() != null
                    && saved.getRecordedAt() != null
                    && saved.getRecordedAt().isBefore(before);
            Map<String, Object> data = deliveryBody(saved);
            data.put("replayed", replayed);
            return ResponseEntity.status(replayed ? HttpStatus.OK : HttpStatus.CREATED)
                    .body(envelope(ctx, data));
        } catch (DeliveryRecordService.DuplicateDeliveryException conflict) {
            log.info("delivery.record.conflict pregnancy={} existing={}",
                    request.pregnancyEpisodeId(), conflict.getExistingDeliveryRecordId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("code", "DELIVERY_ALREADY_RECORDED");
            data.put("message", conflict.getMessage());
            data.put("existing_delivery_record_id", str(conflict.getExistingDeliveryRecordId()));
            data.put("reconciliation_task",
                    "Open the existing delivery record for this pregnancy. Twins are one delivery "
                            + "with a higher babiesDelivered count, not a second row.");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(envelope(ctx, data));
        }
    }

    private static Map<String, Object> intentionBody(ReproductiveIntentionEntity e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("intention_id", str(e.getIntentionId()));
        data.put("subject_cpid", e.getSubjectCpid());
        data.put("intention", e.getIntention());
        data.put("timeframe_months", e.getTimeframeMonths());
        data.put("desired_family_size", e.getDesiredFamilySize());
        data.put("partner_intention", e.getPartnerIntention());
        data.put("fertility_concern", e.getFertilityConcern());
        data.put("contraception_desired", e.getContraceptionDesired());
        data.put("unmet_need", e.getUnmetNeed());
        data.put("notes", e.getNotes());
        data.put("status", e.getStatus());
        data.put("client_offline_id", e.getClientOfflineId());
        return data;
    }

    private static Map<String, Object> preconceptionBody(PreconceptionPlanEntity e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("preconception_plan_id", str(e.getPreconceptionPlanId()));
        data.put("subject_cpid", e.getSubjectCpid());
        data.put("status", e.getStatus());
        data.put("opened_on", str(e.getOpenedOn()));
        data.put("folic_acid_started_on", str(e.getFolicAcidStartedOn()));
        data.put("folic_acid_dose_mg", e.getFolicAcidDoseMg());
        data.put("client_offline_id", e.getClientOfflineId());
        return data;
    }

    private static Map<String, Object> fertilityBody(FertilityEpisodeEntity e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fertility_episode_id", str(e.getFertilityEpisodeId()));
        data.put("subject_cpid", e.getSubjectCpid());
        data.put("status", e.getStatus());
        data.put("opened_on", str(e.getOpenedOn()));
        data.put("months_trying", e.getMonthsTrying());
        data.put("client_offline_id", e.getClientOfflineId());
        return data;
    }

    private static Map<String, Object> deliveryBody(DeliveryRecordEntity e) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("delivery_record_id", str(e.getDeliveryRecordId()));
        data.put("mother_cpid", e.getMotherCpid());
        data.put("pregnancy_episode_id", str(e.getPregnancyEpisodeId()));
        data.put("delivered_at", str(e.getDeliveredAt()));
        data.put("delivery_mode", e.getDeliveryMode());
        data.put("babies_delivered", e.getBabiesDelivered());
        data.put("client_offline_id", e.getClientOfflineId());
        return data;
    }

    // ── envelopes ─────────────────────────────────────────────────────────────

    private static Map<String, Object> envelope(TrustContext ctx, Map<String, Object> data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("meta", Map.of("correlation_id", String.valueOf(ctx.correlationId())));
        return body;
    }

    private static Map<String, Object> conflictBody(String code, String message, UUID existingId,
                                                    String reconciliationTask) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", code);
        data.put("message", message);
        data.put("existing_pregnancy_episode_id", str(existingId));
        data.put("reconciliation_task", reconciliationTask);
        return data;
    }

    /**
     * A refused write, as 422 with a reason.
     *
     * <p>Not 400: the request parsed and the client is not malformed, the content is clinically
     * incomplete. The distinction matters to an offline client deciding whether to retry the same
     * packet (never useful for a 422) or to surface the reason to the person who captured it.
     */
    private static ResponseEntity<Map<String, Object>> unprocessable(TrustContext ctx, String code,
                                                                     String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", code);
        data.put("message", message);
        return ResponseEntity.unprocessableEntity().body(envelope(ctx, data));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
