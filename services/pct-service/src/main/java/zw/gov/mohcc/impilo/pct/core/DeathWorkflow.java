package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.core.death.CauseOfDeathValidator;
import zw.gov.mohcc.impilo.pct.core.death.MedicoLegalScreener;
import zw.gov.mohcc.impilo.pct.core.death.PublicHealthScreener;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.persistence.entity.BodyCustodyEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.DeathAuditEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.DeathCaseEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.FieldBodyManagementEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.VerbalAutopsyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.BodyCustodyRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.DeathAuditRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.DeathCaseRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.FieldBodyManagementRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.VerbalAutopsyRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the in-facility death recording and UBOMI (civil registration)
 * notification workflow.
 *
 * <p>When a patient death is pronounced in a facility, this workflow:</p>
 * <ol>
 *   <li>Records the death with the pronouncing clinician and timestamp</li>
 *   <li>Transitions the journey to {@link JourneyState#DEATH_RECORDED} (terminal)</li>
 *   <li>Tracks UBOMI civil registration notification status</li>
 *   <li>Tracks attachment of the death certificate document</li>
 * </ol>
 *
 * <p>The UBOMI (Zimbabwe Civil Registration and Vital Statistics) integration
 * ensures that facility-level deaths are reported to the national CRVS
 * system. The {@code ubomiNotificationId} and {@code ubomiStatus} fields
 * track the lifecycle of this notification.</p>
 */
@Service
public class DeathWorkflow {

    private static final Logger log = LoggerFactory.getLogger(DeathWorkflow.class);

    /** Clinical roles permitted to confirm/pronounce a death. */
    private static final Set<String> CONFIRM_ROLES = Set.of(
            "DOCTOR", "MEDICAL_OFFICER", "CONSULTANT", "REGISTRAR",
            "CLINICAL_OFFICER", "NURSE_PRESCRIBER", "PLATFORM_ADMIN");

    /** Roles whose licence/scope permits cause-of-death certification. */
    private static final Set<String> CERTIFIER_ROLES = Set.of(
            "DOCTOR", "MEDICAL_OFFICER", "CONSULTANT", "REGISTRAR", "PLATFORM_ADMIN");

    private final DeathCaseRepository deathCaseRepository;
    private final JourneyRepository journeyRepository;
    private final JourneyStateMachine journeyStateMachine;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final BodyCustodyRepository bodyCustodyRepository;
    private final DeathAuditRepository deathAuditRepository;
    private final VerbalAutopsyRepository verbalAutopsyRepository;
    private final FieldBodyManagementRepository fieldBodyManagementRepository;
    private final MedicoLegalScreener medicoLegalScreener;
    private final PublicHealthScreener publicHealthScreener;
    private final CauseOfDeathValidator causeOfDeathValidator;

    public DeathWorkflow(DeathCaseRepository deathCaseRepository,
                         JourneyRepository journeyRepository,
                         JourneyStateMachine journeyStateMachine,
                         EventOutboxRepository outboxRepository,
                         ObjectMapper objectMapper,
                         BodyCustodyRepository bodyCustodyRepository,
                         DeathAuditRepository deathAuditRepository,
                         VerbalAutopsyRepository verbalAutopsyRepository,
                         FieldBodyManagementRepository fieldBodyManagementRepository,
                         MedicoLegalScreener medicoLegalScreener,
                         PublicHealthScreener publicHealthScreener,
                         CauseOfDeathValidator causeOfDeathValidator) {
        this.deathCaseRepository = deathCaseRepository;
        this.journeyRepository = journeyRepository;
        this.journeyStateMachine = journeyStateMachine;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.bodyCustodyRepository = bodyCustodyRepository;
        this.deathAuditRepository = deathAuditRepository;
        this.verbalAutopsyRepository = verbalAutopsyRepository;
        this.fieldBodyManagementRepository = fieldBodyManagementRepository;
        this.medicoLegalScreener = medicoLegalScreener;
        this.publicHealthScreener = publicHealthScreener;
        this.causeOfDeathValidator = causeOfDeathValidator;
    }

    /**
     * Record a patient death during their facility journey.
     *
     * <p>Creates a death case entity and transitions the journey to
     * {@link JourneyState#DEATH_RECORDED}. This is a terminal journey
     * state — no further state transitions are possible.</p>
     *
     * @param journeyId    the patient journey
     * @param pronouncedBy the identifier or name of the clinician who pronounced death
     * @param pronouncedAt the date/time of death pronouncement
     * @return the created death case entity
     * @throws IllegalArgumentException if the journey is not found
     */
    @Transactional
    public DeathCaseEntity recordDeath(String journeyId, String pronouncedBy,
                                       OffsetDateTime pronouncedAt) {
        TrustContext ctx = TrustContextHolder.require();

        JourneyEntity journey = journeyRepository.findByJourneyId(journeyId)
                .orElseThrow(() -> new IllegalArgumentException("Journey not found: " + journeyId));

        DeathCaseEntity deathCase = new DeathCaseEntity();
        deathCase.setId(UUID.randomUUID());
        deathCase.setJourneyId(journeyId);
        deathCase.setTenantId(ctx.tenantId());
        deathCase.setFacilityId(journey.getFacilityId());
        deathCase.setPatientCpid(journey.getPatientCpid());
        deathCase.setPronouncedBy(pronouncedBy);
        deathCase.setPronouncedAt(pronouncedAt);
        deathCase.setStatus("RECORDED");
        deathCase.setCreatedAt(OffsetDateTime.now());

        deathCase = deathCaseRepository.save(deathCase);

        // Transition journey to terminal DEATH_RECORDED state
        journeyStateMachine.transition(journey, JourneyState.DEATH_RECORDED);

        // Outbox event for UBOMI notification service and analytics
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", deathCase.getId().toString());
        payload.put("journeyId", journeyId);
        payload.put("patientCpid", journey.getPatientCpid());
        payload.put("facilityId", journey.getFacilityId().toString());
        payload.put("pronouncedBy", pronouncedBy);
        payload.put("pronouncedAt", pronouncedAt.toString());
        writeOutbox("DEATH_CASE", deathCase.getId().toString(),
                "DEATH_RECORDED", toJson(payload));

        log.info("Death recorded: case={}, journey={}, pronounced by {} at {}",
                deathCase.getId(), journeyId, pronouncedBy, pronouncedAt);

        return deathCase;
    }

    /**
     * Update the UBOMI (civil registration) notification status for a death case.
     *
     * <p>Called by the integration layer when UBOMI acknowledges or updates
     * the death notification. Tracks the external notification identifier
     * and its current status in the CRVS system.</p>
     *
     * @param caseId              the death case to update
     * @param ubomiNotificationId the UBOMI-assigned notification reference
     * @param ubomiStatus         the current UBOMI processing status (e.g. SUBMITTED, ACKNOWLEDGED, REGISTERED)
     * @return the updated death case entity
     * @throws IllegalArgumentException if the case is not found
     */
    @Transactional
    public DeathCaseEntity updateUbomiStatus(UUID caseId, String ubomiNotificationId,
                                              String ubomiStatus) {
        DeathCaseEntity deathCase = deathCaseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Death case not found: " + caseId));

        deathCase.setUbomiNotificationId(ubomiNotificationId);
        deathCase.setUbomiStatus(ubomiStatus);

        deathCase = deathCaseRepository.save(deathCase);

        log.info("UBOMI status updated: case={}, notificationId={}, status={}",
                caseId, ubomiNotificationId, ubomiStatus);

        return deathCase;
    }

    /**
     * Attach a death certificate document reference to a death case.
     *
     * <p>The certificate document is stored in the Document Service (MinIO/S3)
     * and referenced here by its document identifier for cross-service
     * traceability.</p>
     *
     * @param caseId    the death case
     * @param certDocId the document identifier from the Document Service
     * @return the updated death case entity
     * @throws IllegalArgumentException if the case is not found
     */
    @Transactional
    public DeathCaseEntity attachCertificate(UUID caseId, String certDocId) {
        DeathCaseEntity deathCase = deathCaseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Death case not found: " + caseId));

        deathCase.setCertDocId(certDocId);

        deathCase = deathCaseRepository.save(deathCase);

        log.info("Death certificate attached: case={}, docId={}", caseId, certDocId);

        return deathCase;
    }

    /**
     * Close the death case workflow.
     *
     * <p>Sets the {@code closedAt} timestamp and publishes a completion
     * event for downstream consumers (analytics, registry updates).</p>
     *
     * @param caseId the death case to close
     * @return the closed death case entity
     * @throws IllegalArgumentException if the case is not found
     * @throws IllegalStateException    if the case is not in RECORDED status
     */
    @Transactional
    public DeathCaseEntity completeDeath(UUID caseId) {
        DeathCaseEntity deathCase = deathCaseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Death case not found: " + caseId));

        if (!"RECORDED".equals(deathCase.getStatus())) {
            throw new IllegalStateException(
                    "Cannot complete death case in status: " + deathCase.getStatus());
        }

        deathCase.setStatus("COMPLETED");
        deathCase.setClosedAt(OffsetDateTime.now());

        deathCase = deathCaseRepository.save(deathCase);

        // Outbox event
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", caseId.toString());
        payload.put("journeyId", deathCase.getJourneyId());
        payload.put("patientCpid", deathCase.getPatientCpid());
        payload.put("closedAt", deathCase.getClosedAt().toString());
        payload.put("ubomiNotificationId", deathCase.getUbomiNotificationId());
        payload.put("ubomiStatus", deathCase.getUbomiStatus());
        payload.put("certDocId", deathCase.getCertDocId());
        writeOutbox("DEATH_CASE", caseId.toString(),
                "DEATH_CASE_COMPLETED", toJson(payload));

        log.info("Death case completed: case={}, journey={}", caseId, deathCase.getJourneyId());

        return deathCase;
    }

    // ==================================================================
    // WS#8 — Death & Post-Death Pathway spine
    // ==================================================================

    /**
     * Confirm a death by an authorised provider and open (or update) the death case, then run
     * the medico-legal screen. Hard block: only an authorised clinical role may confirm a death.
     */
    @Transactional
    public DeathCaseEntity confirmDeath(zw.gov.mohcc.impilo.pct.api.dto.ConfirmDeathRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        String role = roleOf(ctx);
        if (!CONFIRM_ROLES.contains(role)) {
            throw new SecurityException("Role " + role + " is not authorised to confirm a death");
        }

        UUID facilityId = req.facilityId() != null ? UUID.fromString(req.facilityId()) : ctx.facilityId();

        DeathCaseEntity dc = null;
        if (req.journeyId() != null) {
            dc = deathCaseRepository.findByTenantIdAndJourneyId(ctx.tenantId(), req.journeyId()).orElse(null);
        }
        boolean isNew = dc == null;
        if (isNew) {
            dc = new DeathCaseEntity();
            dc.setId(UUID.randomUUID());
            dc.setTenantId(ctx.tenantId());
            dc.setJourneyId(req.journeyId());
            dc.setCreatedAt(OffsetDateTime.now());
            dc.setStatus("RECORDED");
        }
        dc.setFacilityId(facilityId);
        if (req.deceasedCpid() != null) dc.setPatientCpid(req.deceasedCpid());
        dc.setDeathDatetime(req.deathDatetime());
        dc.setPronouncedAt(req.deathDatetime());
        dc.setPlaceOfDeathContext(req.placeOfDeathContext());
        dc.setPlaceOfDeathLocation(req.placeOfDeathLocation());
        dc.setResuscitationAttempted(req.resuscitationAttempted());
        dc.setPresentAtDeath(req.presentAtDeath());
        dc.setConfirmedBy(ctx.actorId());
        dc.setPronouncedBy(ctx.actorId());
        dc.setConfirmedByRole(role);
        dc.setConfirmedAt(OffsetDateTime.now());
        dc.setDeceasedIdentityStatus(
                req.deceasedIdentityStatus() != null ? req.deceasedIdentityStatus() : "KNOWN");
        if (!"KNOWN".equalsIgnoreCase(dc.getDeceasedIdentityStatus()) && dc.getTemporaryIdentityRef() == null) {
            // Unknown / brought-in-dead body → mint a temporary deceased identity reference.
            dc.setTemporaryIdentityRef("TMP-DEC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        if (req.sourceContext() != null) dc.setSourceContext(req.sourceContext());
        if (req.sourceRef() != null) dc.setSourceRef(req.sourceRef());
        dc.setBodyDispositionContext(req.bodyDispositionContext() != null
                ? req.bodyDispositionContext()
                : defaultBodyDisposition(req.placeOfDeathContext(), dc.getSourceContext()));

        // ── Medico-legal screen ──
        MedicoLegalScreener.Result ml = medicoLegalScreener.evaluate(
                req.admittedAt(), req.deathDatetime(), req.placeOfDeathContext(),
                req.suspectedManner(), Boolean.TRUE.equals(req.inCustody()),
                dc.getDeceasedIdentityStatus());
        dc.setCoronerReferralRequired(ml.coronerReferralRequired());
        dc.setMedicoLegalTriggers(String.join(",", ml.triggers()));
        if (ml.coronerReferralRequired()) {
            dc.setCoronerReferralStatus("PENDING_REFERRAL"); // owner-routed hook to coroner/police
            dc.setBodyReleaseBlocked(true);                  // body release blocked until cleared
            if (dc.getCodManner() == null) dc.setCodManner("PENDING");
            // Cause is not yet established — a coroner referral is pending, NOT medically certified.
            if (dc.getCauseOfDeathBasis() == null) dc.setCauseOfDeathBasis("MEDICO_LEGAL_PENDING");
        } else if (dc.getCauseOfDeathBasis() == null) {
            dc.setCauseOfDeathBasis("UNKNOWN_UNCERTIFIED"); // set on certification / verbal autopsy
        }

        dc = deathCaseRepository.save(dc);

        if (isNew && req.journeyId() != null) {
            journeyRepository.findByJourneyId(req.journeyId()).ifPresent(j ->
                    journeyStateMachine.transition(j, JourneyState.DEATH_RECORDED));
        }

        audit(dc, "CONFIRMED", null, null, dc.getDeathDatetime() == null ? null : dc.getDeathDatetime().toString(),
                "Death confirmed", ctx, role);
        if (ml.coronerReferralRequired()) {
            audit(dc, "MEDICO_LEGAL_SCREENED", "coroner_referral_required", "false", "true",
                    "Medico-legal triggers: " + dc.getMedicoLegalTriggers(), ctx, role);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", dc.getId().toString());
        payload.put("journeyId", dc.getJourneyId());
        payload.put("coronerReferralRequired", dc.isCoronerReferralRequired());
        payload.put("sourceContext", dc.getSourceContext());
        payload.put("bodyDispositionContext", dc.getBodyDispositionContext());
        payload.put("causeOfDeathBasis", dc.getCauseOfDeathBasis());
        writeOutbox("DEATH_CASE", dc.getId().toString(), "DEATH_CONFIRMED", toJson(payload));

        log.info("Death confirmed: case={}, journey={}, source={}, by role {}, coronerReferral={}",
                dc.getId(), dc.getJourneyId(), dc.getSourceContext(), role, dc.isCoronerReferralRequired());
        return dc;
    }

    /**
     * Confirm a body brought in dead to a facility / mortuary / police point. Forces the
     * brought-in-dead place + source and a facility body-disposition, then runs the same authorised
     * confirmation + medico-legal screen as {@link #confirmDeath}. Facility body receipt, mortuary
     * custody, and medico-legal screening apply (this is NOT a community/field death).
     */
    @Transactional
    public DeathCaseEntity confirmBroughtInDead(zw.gov.mohcc.impilo.pct.api.dto.ConfirmDeathRequest req) {
        zw.gov.mohcc.impilo.pct.api.dto.ConfirmDeathRequest forced =
                new zw.gov.mohcc.impilo.pct.api.dto.ConfirmDeathRequest(
                        req.journeyId(), req.deathDatetime(), "BROUGHT_IN_DEAD", req.placeOfDeathLocation(),
                        req.resuscitationAttempted(), req.presentAtDeath(), req.deceasedCpid(),
                        req.deceasedIdentityStatus(), req.admittedAt(), req.suspectedManner(),
                        req.inCustody(), req.facilityId(), "BROUGHT_IN_DEAD",
                        req.sourceRef(),
                        req.bodyDispositionContext() != null ? req.bodyDispositionContext() : "BROUGHT_TO_FACILITY");
        return confirmDeath(forced);
    }

    /**
     * Report a community / field death by a non-confirming actor (CHW, family, surveillance). This is
     * an UNVERIFIED notification, NOT a provider confirmation: the case opens in status REPORTED with
     * {@code cause_of_death_basis = UNKNOWN_UNCERTIFIED}, no mortuary custody, and no asserted cause.
     * A red-flag manner still routes it to the medico-legal pathway. The body may never come to a
     * facility. No confirm-role gate — a report can legitimately originate from a non-clinical actor.
     */
    @Transactional
    public DeathCaseEntity reportCommunityDeath(
            zw.gov.mohcc.impilo.pct.api.dto.CommunityDeathReportRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        String role = roleOf(ctx);

        UUID facilityId = req.facilityId() != null ? UUID.fromString(req.facilityId()) : ctx.facilityId();
        String source = req.sourceContext() != null ? req.sourceContext() : "COMMUNITY_FIELD";
        if ("FACILITY".equals(source) || "BROUGHT_IN_DEAD".equals(source)
                || "INPATIENT_DISCHARGE".equals(source) || "EMERGENCY_INCIDENT".equals(source)) {
            throw new IllegalArgumentException(
                    "A community death report requires a community/field source_context, not " + source);
        }

        DeathCaseEntity dc = new DeathCaseEntity();
        dc.setId(UUID.randomUUID());
        dc.setTenantId(ctx.tenantId());
        dc.setFacilityId(facilityId);
        dc.setStatus("REPORTED");
        dc.setDeathDatetime(req.deathDatetime());
        dc.setPronouncedAt(req.deathDatetime());
        dc.setPlaceOfDeathContext(req.placeOfDeathContext() != null ? req.placeOfDeathContext() : "COMMUNITY");
        dc.setPlaceOfDeathLocation(req.placeOfDeathLocation());
        if (req.deceasedCpid() != null) dc.setPatientCpid(req.deceasedCpid());
        dc.setDeceasedIdentityStatus(
                req.deceasedIdentityStatus() != null ? req.deceasedIdentityStatus() : "UNKNOWN");
        if (!"KNOWN".equalsIgnoreCase(dc.getDeceasedIdentityStatus())) {
            dc.setTemporaryIdentityRef("TMP-DEC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        dc.setSourceContext(source);
        dc.setBodyDispositionContext(req.bodyDispositionContext() != null
                ? req.bodyDispositionContext() : "NOT_BROUGHT_TO_FACILITY");
        dc.setReporterName(req.reporterName());
        dc.setReporterRole(req.reporterRole());
        dc.setReporterContact(req.reporterContact());
        dc.setCreatedAt(OffsetDateTime.now());

        // A report is unverified — no confirming provider, no asserted cause yet.
        MedicoLegalScreener.Result ml = medicoLegalScreener.evaluate(
                null, req.deathDatetime(), dc.getPlaceOfDeathContext(),
                req.suspectedManner(), Boolean.TRUE.equals(req.inCustody()),
                dc.getDeceasedIdentityStatus());
        dc.setCoronerReferralRequired(ml.coronerReferralRequired());
        dc.setMedicoLegalTriggers(String.join(",", ml.triggers()));
        if (ml.coronerReferralRequired()) {
            dc.setCoronerReferralStatus("PENDING_REFERRAL");
            dc.setCauseOfDeathBasis("MEDICO_LEGAL_PENDING");
            if (dc.getCodManner() == null) dc.setCodManner("PENDING");
        } else {
            dc.setCauseOfDeathBasis("UNKNOWN_UNCERTIFIED");
        }

        dc = deathCaseRepository.save(dc);

        audit(dc, "COMMUNITY_REPORTED", "source_context", null, source,
                "Community/field death reported by " + (req.reporterRole() != null ? req.reporterRole() : role)
                        + " (unverified)", ctx, role);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("caseId", dc.getId().toString());
        p.put("sourceContext", source);
        p.put("deceasedIdentityStatus", dc.getDeceasedIdentityStatus());
        p.put("coronerReferralRequired", dc.isCoronerReferralRequired());
        writeOutbox("DEATH_CASE", dc.getId().toString(), "DEATH_COMMUNITY_REPORTED", toJson(p));

        log.info("Community death reported: case={}, source={}, reporter={}, coronerReferral={}",
                dc.getId(), source, req.reporterRole(), dc.isCoronerReferralRequired());
        return dc;
    }

    /**
     * Record a verbal-autopsy interview for a community/field death (WHO VA standard). Yields a
     * PROBABLE cause only — never a medical certification. Hard rule: a VA may NOT be attached to a
     * medically certified (or post-mortem certified) death — VA-inferred causes are never mixed with
     * certified ones in mortality intelligence. If red flags are present the case is routed to the
     * medico-legal pathway and NO probable cause is assigned.
     */
    @Transactional
    public VerbalAutopsyEntity recordVerbalAutopsy(UUID caseId,
            zw.gov.mohcc.impilo.pct.api.dto.VerbalAutopsyRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        String role = roleOf(ctx);
        DeathCaseEntity dc = require(ctx, caseId);

        String basis = dc.getCauseOfDeathBasis();
        if ("MEDICALLY_CERTIFIED".equals(basis) || "POST_MORTEM_CERTIFIED".equals(basis)
                || "SIGNED".equals(dc.getCertificationStatus()) || "AMENDED".equals(dc.getCertificationStatus())) {
            throw new IllegalStateException(
                    "This death already has a medically certified cause — a verbal-autopsy probable "
                            + "cause must not be mixed with a certified cause.");
        }

        List<String> redFlags = req.redFlags() == null ? List.of()
                : req.redFlags().stream().filter(f -> f != null && !f.isBlank()).toList();

        VerbalAutopsyEntity va = new VerbalAutopsyEntity();
        va.setVaId(UUID.randomUUID());
        va.setTenantId(ctx.tenantId());
        va.setCaseId(caseId);
        va.setFacilityId(dc.getFacilityId());
        va.setInstrument(req.instrument() != null ? req.instrument() : "WHO_VA_2022");
        va.setIntervieweeRelationship(req.intervieweeRelationship());
        va.setInterviewedAt(req.interviewedAt());
        va.setInterviewerId(ctx.actorId());
        va.setInterviewerRole(role);
        va.setNarrative(req.narrative());
        va.setCauseAssignmentMethod(req.causeAssignmentMethod());
        va.setRecordedBy(ctx.actorId());
        va.setRecordedByRole(role);
        va.setRedFlags(redFlags.isEmpty() ? null : String.join(",", redFlags));

        if (!redFlags.isEmpty()) {
            // Red flags trump verbal autopsy — route to the medico-legal pathway, assign no cause.
            va.setStatus("DRAFT");
            va = verbalAutopsyRepository.save(va);
            dc.setCoronerReferralRequired(true);
            if (dc.getCoronerReferralStatus() == null) dc.setCoronerReferralStatus("PENDING_REFERRAL");
            dc.setMedicoLegalTriggers(mergeTriggers(dc.getMedicoLegalTriggers(), redFlags));
            dc.setCauseOfDeathBasis("MEDICO_LEGAL_PENDING");
            deathCaseRepository.save(dc);
            audit(dc, "VERBAL_AUTOPSY_RED_FLAGGED", "red_flags", null, va.getRedFlags(),
                    "Verbal autopsy surfaced medico-legal red flags — routed to coroner/investigation",
                    ctx, role);
            Map<String, Object> rp = new LinkedHashMap<>();
            rp.put("caseId", caseId.toString());
            rp.put("redFlags", va.getRedFlags());
            writeOutbox("DEATH_CASE", caseId.toString(), "DEATH_MEDICO_LEGAL_FLAGGED", toJson(rp));
            return va;
        }

        va.setProbableImmediateCause(req.probableImmediateCause());
        va.setProbableUnderlyingCause(req.probableUnderlyingCause());
        va.setProbableCauseCode(req.probableCauseCode());
        va.setStatus(req.complete() ? "COMPLETED" : "DRAFT");
        va = verbalAutopsyRepository.save(va);

        if (req.complete()) {
            // Assign the PROBABLE cause on the case — NOT a certification. certification_status stays
            // NOT_STARTED; the basis marks this as verbal-autopsy-probable so mortality intelligence
            // never treats it as certified.
            dc.setCodImmediate(req.probableImmediateCause());
            dc.setCodUnderlying(req.probableUnderlyingCause());
            dc.setCauseOfDeathBasis("VERBAL_AUTOPSY_PROBABLE");
            deathCaseRepository.save(dc);
        }

        audit(dc, req.complete() ? "VERBAL_AUTOPSY_COMPLETED" : "VERBAL_AUTOPSY_DRAFTED",
                "cause_of_death_basis", basis, dc.getCauseOfDeathBasis(),
                "Verbal autopsy " + va.getStatus().toLowerCase(), ctx, role);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("caseId", caseId.toString());
        p.put("vaId", va.getVaId().toString());
        p.put("status", va.getStatus());
        p.put("causeOfDeathBasis", dc.getCauseOfDeathBasis());
        writeOutbox("DEATH_CASE", caseId.toString(), "DEATH_VERBAL_AUTOPSY", toJson(p));
        return va;
    }

    /**
     * Record non-facility (field) body management — safe &amp; dignified burial, family release,
     * funeral-director direct, police handover, or unrecovered. This is the branch for a body that
     * never comes to a facility; it does NOT require mortuary custody. A non-police disposition is
     * refused while an open medico-legal / coroner referral is present (that body must go to the
     * competent authority). Infectious-risk deaths emit a public-health signal.
     */
    @Transactional
    public FieldBodyManagementEntity recordFieldBodyManagement(UUID caseId,
            zw.gov.mohcc.impilo.pct.api.dto.FieldBodyManagementRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        String role = roleOf(ctx);
        DeathCaseEntity dc = require(ctx, caseId);

        String disposition = req.dispositionType();
        boolean openMedicoLegal = dc.isCoronerReferralRequired()
                && !"CLEARED".equals(dc.getCoronerReferralStatus());
        if (openMedicoLegal && !"POLICE_HANDOVER".equals(disposition)) {
            throw new IllegalStateException(
                    "An open medico-legal / coroner referral is present — the body must be handed to the "
                            + "competent authority (POLICE_HANDOVER), not released or buried.");
        }

        FieldBodyManagementEntity fbm = new FieldBodyManagementEntity();
        fbm.setFbmId(UUID.randomUUID());
        fbm.setTenantId(ctx.tenantId());
        fbm.setCaseId(caseId);
        fbm.setFacilityId(dc.getFacilityId());
        fbm.setDispositionType(disposition);
        fbm.setInfectiousRisk(req.infectiousRisk());
        fbm.setInfectiousHazard(req.infectiousHazard());
        fbm.setSafeBurialProtocolApplied(req.safeBurialProtocolApplied());
        fbm.setPpeUsed(req.ppeUsed());
        fbm.setHandlingTeam(req.handlingTeam());
        fbm.setFamilyPresent(req.familyPresent());
        fbm.setReligiousRitesObserved(req.religiousRitesObserved());
        fbm.setReleasedToName(req.releasedToName());
        fbm.setReleasedToRelationship(req.releasedToRelationship());
        fbm.setLocation(req.location());
        fbm.setOccurredAt(req.occurredAt());
        fbm.setNotes(req.notes());
        fbm.setManagedBy(ctx.actorId());
        fbm.setManagedByRole(role);
        fbm = fieldBodyManagementRepository.save(fbm);

        dc.setBodyDispositionContext(dispositionToBodyContext(disposition));
        deathCaseRepository.save(dc);

        audit(dc, "FIELD_BODY_MANAGED", "body_disposition_context", null, dc.getBodyDispositionContext(),
                "Field body management: " + disposition
                        + (req.infectiousRisk() ? " (infectious risk)" : ""), ctx, role);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("caseId", caseId.toString());
        p.put("dispositionType", disposition);
        p.put("bodyDispositionContext", dc.getBodyDispositionContext());
        p.put("infectiousRisk", req.infectiousRisk());
        writeOutbox("DEATH_CASE", caseId.toString(), "DEATH_FIELD_BODY_MANAGED", toJson(p));
        if (req.infectiousRisk()) {
            // owner-routed: surveillance picks up the public-health signal (safe & dignified burial).
            writeOutbox("DEATH_CASE", caseId.toString(), "DEATH_PUBLIC_HEALTH_SIGNAL", toJson(p));
        }
        return fbm;
    }

    /**
     * WS#6 seam: open a death case when an inpatient discharge disposition is DEATH. Idempotent —
     * if a death case already exists for the journey it is returned unchanged. The confirming actor
     * here is the discharging clinician already authorised by the discharge gate, so this path does
     * not re-impose the confirm-role block (it is reached only via the discharge workflow).
     */
    @Transactional
    public DeathCaseEntity openFromInpatientDischarge(String journeyId, OffsetDateTime deathDatetime) {
        TrustContext ctx = TrustContextHolder.require();
        DeathCaseEntity existing = deathCaseRepository
                .findByTenantIdAndJourneyId(ctx.tenantId(), journeyId).orElse(null);
        if (existing != null) {
            return existing;
        }
        JourneyEntity journey = journeyRepository.findByJourneyId(journeyId).orElse(null);
        DeathCaseEntity dc = new DeathCaseEntity();
        dc.setId(UUID.randomUUID());
        dc.setTenantId(ctx.tenantId());
        dc.setJourneyId(journeyId);
        dc.setFacilityId(journey != null ? journey.getFacilityId() : ctx.facilityId());
        dc.setPatientCpid(journey != null ? journey.getPatientCpid() : null);
        dc.setStatus("RECORDED");
        dc.setDeathDatetime(deathDatetime);
        dc.setPronouncedAt(deathDatetime);
        dc.setConfirmedBy(ctx.actorId());
        dc.setPronouncedBy(ctx.actorId());
        dc.setConfirmedByRole(roleOf(ctx));
        dc.setConfirmedAt(OffsetDateTime.now());
        dc.setPlaceOfDeathContext("INPATIENT");
        dc.setSourceContext("INPATIENT_DISCHARGE");
        dc.setSourceRef(journeyId);
        dc.setCreatedAt(OffsetDateTime.now());
        dc = deathCaseRepository.save(dc);
        audit(dc, "CONFIRMED", "source_context", null, "INPATIENT_DISCHARGE",
                "Death case opened from inpatient discharge disposition=DEATH (WS#6 seam)", ctx, roleOf(ctx));
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("caseId", dc.getId().toString());
        p.put("journeyId", journeyId);
        p.put("sourceContext", "INPATIENT_DISCHARGE");
        writeOutbox("DEATH_CASE", dc.getId().toString(), "DEATH_CONFIRMED", toJson(p));
        log.info("Death case opened from inpatient discharge: case={}, journey={}", dc.getId(), journeyId);
        return dc;
    }

    /** Run / re-run the public-health (mortality-review) screen. */
    @Transactional
    public DeathCaseEntity screenPublicHealth(UUID caseId,
            zw.gov.mohcc.impilo.pct.api.dto.PublicHealthScreenRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        DeathCaseEntity dc = require(ctx, caseId);
        PublicHealthScreener.Result r = publicHealthScreener.evaluate(
                req.ageYears(), req.ageDays(),
                Boolean.TRUE.equals(req.maternal()), Boolean.TRUE.equals(req.perinatal()),
                Boolean.TRUE.equals(req.stillbirth()), Boolean.TRUE.equals(req.notifiableCause()),
                Boolean.TRUE.equals(req.outbreakLinked()));
        dc.setPublicHealthFlags(String.join(",", r.flags()));
        dc.setDeathReviewRequired(r.deathReviewRequired());
        dc = deathCaseRepository.save(dc);
        audit(dc, "PUBLIC_HEALTH_SCREENED", "public_health_flags", null, dc.getPublicHealthFlags(),
                "Death review required: " + r.deathReviewRequired(), ctx, roleOf(ctx));
        if (r.deathReviewRequired()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("caseId", caseId.toString());
            p.put("flags", dc.getPublicHealthFlags());
            // owner-routed: Rito M&M / MDSR review picks this up
            writeOutbox("DEATH_CASE", caseId.toString(), "DEATH_REVIEW_REQUIRED", toJson(p));
        }
        return dc;
    }

    /**
     * Draft or sign the WHO cause-of-death certificate. Hard block: ineligible certifier role is
     * rejected; certification is refused while medico-legal triggers are present and not cleared
     * (routine certification cannot proceed under an open coroner referral). Garbage-cause warnings
     * are SOFT and recorded, never blocking.
     */
    @Transactional
    public DeathCaseEntity certifyCauseOfDeath(UUID caseId,
            zw.gov.mohcc.impilo.pct.api.dto.CertifyCauseOfDeathRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        String role = roleOf(ctx);
        if (!CERTIFIER_ROLES.contains(role)) {
            throw new SecurityException("Role " + role + " is not eligible to certify cause of death");
        }
        if (req.certifierLicence() == null || req.certifierLicence().isBlank()) {
            throw new SecurityException("A certifier licence number is required to certify cause of death");
        }
        DeathCaseEntity dc = require(ctx, caseId);

        if (req.sign() && dc.isCoronerReferralRequired()
                && !"CLEARED".equals(dc.getCoronerReferralStatus())) {
            throw new IllegalStateException(
                    "Routine certification is blocked: an open medico-legal / coroner referral is "
                            + "present (" + dc.getMedicoLegalTriggers() + "). The competent authority must clear it.");
        }

        String prevStatus = dc.getCertificationStatus();
        dc.setCodImmediate(req.immediateCause());
        dc.setCodAntecedent(req.antecedentCause());
        dc.setCodUnderlying(req.underlyingCause());
        dc.setCodContributory(req.contributoryCause());
        if (req.manner() != null) dc.setCodManner(req.manner());
        dc.setCodExternalCause(req.externalCause());
        dc.setCertifierId(ctx.actorId());
        dc.setCertifierRole(role);
        dc.setCertifierLicence(req.certifierLicence());

        List<String> warnings = causeOfDeathValidator.warnings(
                req.immediateCause(), req.antecedentCause(), req.underlyingCause(), req.contributoryCause());
        dc.setCertWarnings(warnings.isEmpty() ? null : String.join(" | ", warnings));

        String eventType;
        if (req.sign()) {
            boolean amendment = "SIGNED".equals(prevStatus) || "AMENDED".equals(prevStatus);
            dc.setCertificationStatus(amendment ? "AMENDED" : "SIGNED");
            dc.setCertifiedAt(OffsetDateTime.now());
            // A clinician-signed certificate is a MEDICALLY certified cause — the highest certainty
            // class. This deliberately overrides any prior probable/pending basis; a certified cause
            // supersedes a verbal-autopsy or field-investigation probable one.
            dc.setCauseOfDeathBasis("MEDICALLY_CERTIFIED");
            // Real clinical record: emit a Composition/DocumentReference toward Butano (owner-routed).
            // We persist the intended reference; rendering of a PDF certificate is deferred to Butano.
            dc.setCertCompositionRef("COD-COMP-" + caseId.toString().substring(0, 8).toUpperCase());
            eventType = amendment ? "CERT_AMENDED" : "CERT_SIGNED";
        } else {
            dc.setCertificationStatus("DRAFT");
            eventType = "CERT_DRAFTED";
        }
        dc = deathCaseRepository.save(dc);

        audit(dc, eventType, "underlying_cause", null, req.underlyingCause(),
                warnings.isEmpty() ? "Cause certified" : "Certified with warnings: " + dc.getCertWarnings(),
                ctx, role);

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("caseId", caseId.toString());
        p.put("certificationStatus", dc.getCertificationStatus());
        p.put("underlyingCause", dc.getCodUnderlying());
        p.put("certifierLicence", dc.getCertifierLicence());
        writeOutbox("DEATH_CASE", caseId.toString(), eventType, toJson(p));
        return dc;
    }

    // ── Body / mortuary custody ──

    /** Receive a body into mortuary custody, applying a post-mortem hold when a coroner referral is open. */
    @Transactional
    public BodyCustodyEntity receiveBody(UUID caseId,
            zw.gov.mohcc.impilo.pct.api.dto.BodyReceiveRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        DeathCaseEntity dc = require(ctx, caseId);
        BodyCustodyEntity custody = bodyCustodyRepository.findByTenantIdAndCaseId(ctx.tenantId(), caseId)
                .orElseGet(BodyCustodyEntity::new);
        if (custody.getCustodyId() == null) {
            custody.setCustodyId(UUID.randomUUID());
            custody.setTenantId(ctx.tenantId());
            custody.setCaseId(caseId);
        }
        custody.setFacilityId(dc.getFacilityId());
        custody.setBodyTag(req.bodyTag());
        custody.setStorageLocation(req.storageLocation());
        custody.setStatus("STORED");
        custody.setReceivedBy(ctx.actorId());
        custody.setReceivedAt(OffsetDateTime.now());
        if (dc.isCoronerReferralRequired() && !"CLEARED".equals(dc.getCoronerReferralStatus())) {
            custody.setLegalHold(true);
            custody.setLegalHoldReason("Open medico-legal / coroner referral: " + dc.getMedicoLegalTriggers());
        }
        custody = bodyCustodyRepository.save(custody);
        audit(dc, "BODY_RECEIVED", "body_tag", null, req.bodyTag(),
                "Body received into mortuary custody", ctx, roleOf(ctx));
        return custody;
    }

    /** Place or lift a post-mortem hold (e.g. pending Oros pathology — owner-routed execution). */
    @Transactional
    public BodyCustodyEntity setPostmortemHold(UUID caseId, boolean hold) {
        TrustContext ctx = TrustContextHolder.require();
        DeathCaseEntity dc = require(ctx, caseId);
        BodyCustodyEntity custody = bodyCustodyRepository.findByTenantIdAndCaseId(ctx.tenantId(), caseId)
                .orElseThrow(() -> new IllegalArgumentException("No body custody record for case " + caseId));
        custody.setPostmortemHold(hold);
        custody = bodyCustodyRepository.save(custody);
        audit(dc, "BODY_RELEASE_BLOCKED", "postmortem_hold", null, String.valueOf(hold),
                hold ? "Post-mortem hold applied" : "Post-mortem hold lifted", ctx, roleOf(ctx));
        return custody;
    }

    /**
     * Authorise body release to a named recipient. Hard block: release is refused while a legal,
     * coroner, or post-mortem hold is active, or when the case-level body-release block is set.
     */
    @Transactional
    public BodyCustodyEntity releaseBody(UUID caseId,
            zw.gov.mohcc.impilo.pct.api.dto.BodyReleaseRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        DeathCaseEntity dc = require(ctx, caseId);
        BodyCustodyEntity custody = bodyCustodyRepository.findByTenantIdAndCaseId(ctx.tenantId(), caseId)
                .orElseThrow(() -> new IllegalArgumentException("No body custody record for case " + caseId));

        if (custody.isLegalHold() || custody.isPostmortemHold()
                || (dc.isCoronerReferralRequired() && !"CLEARED".equals(dc.getCoronerReferralStatus()))
                || dc.isBodyReleaseBlocked()) {
            throw new IllegalStateException(
                    "Body release is blocked: a legal, coroner, or post-mortem hold is active.");
        }
        if (req.releasedToName() == null || req.releasedToName().isBlank()) {
            throw new IllegalArgumentException("Body may only be released to a named, authorised recipient.");
        }
        custody.setStatus("RELEASED");
        custody.setReleaseAuthorisedBy(ctx.actorId());
        custody.setReleasedToName(req.releasedToName());
        custody.setReleasedToRelationship(req.releasedToRelationship());
        custody.setReleasedToIdRef(req.releasedToIdRef());
        custody.setReleasedAt(OffsetDateTime.now());
        custody = bodyCustodyRepository.save(custody);
        audit(dc, "BODY_RELEASED", "released_to_name", null, req.releasedToName(),
                "Body released to authorised recipient", ctx, roleOf(ctx));
        return custody;
    }

    /** Clear (or update) the coroner referral status — owner-routed from the competent authority. */
    @Transactional
    public DeathCaseEntity updateCoronerStatus(UUID caseId, String status, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        DeathCaseEntity dc = require(ctx, caseId);
        String old = dc.getCoronerReferralStatus();
        dc.setCoronerReferralStatus(status);
        if ("CLEARED".equals(status)) {
            dc.setBodyReleaseBlocked(false);
        }
        dc = deathCaseRepository.save(dc);
        audit(dc, "STATUS_CHANGE", "coroner_referral_status", old, status,
                reason != null ? reason : "Coroner referral status updated", ctx, roleOf(ctx));
        return dc;
    }

    // ── CRVS / Ubomi handoff ──

    /**
     * Build and stage the civil-registration package for Ubomi. Validates that the required fields
     * are present before the package can be marked READY (no submission of an incomplete package).
     * Execution toward the Registrar General is an owner-routed hook (Ubomi owns CRVS); we never
     * forge a completed registration.
     */
    @Transactional
    public DeathCaseEntity stageCrvsPackage(UUID caseId) {
        TrustContext ctx = TrustContextHolder.require();
        DeathCaseEntity dc = require(ctx, caseId);
        List<String> missing = crvsMissingFields(dc);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Civil-registration package is incomplete — missing: " + String.join(", ", missing));
        }
        dc.setCivilRegistrationStatus("PACKAGE_READY");
        dc = deathCaseRepository.save(dc);
        audit(dc, "CRVS_PACKAGED", "civil_registration_status", null, "PACKAGE_READY",
                "Civil-registration package staged", ctx, roleOf(ctx));
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("caseId", caseId.toString());
        p.put("deceasedCpid", dc.getPatientCpid());
        // owner-routed hook → Ubomi DeathNotification submit / Registrar General
        writeOutbox("DEATH_CASE", caseId.toString(), "CRVS_PACKAGE_READY", toJson(p));
        return dc;
    }

    /**
     * Fields the CRVS package cannot be submitted without. A cause is satisfied either by a signed
     * medical certificate OR — for a community/field death that never entered a facility — by a
     * probable cause from verbal autopsy or field investigation. The probable-vs-certified distinction
     * is preserved on the notification via {@code cause_of_death_basis}; it is not flattened here.
     */
    public List<String> crvsMissingFields(DeathCaseEntity dc) {
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        if (dc.getDeathDatetime() == null) missing.add("dateOfDeath");
        if (dc.getPatientCpid() == null && dc.getTemporaryIdentityRef() == null) missing.add("deceasedIdentity");
        if (dc.getPlaceOfDeathContext() == null) missing.add("placeOfDeath");
        boolean certified = "SIGNED".equals(dc.getCertificationStatus())
                || "AMENDED".equals(dc.getCertificationStatus());
        boolean probable = "VERBAL_AUTOPSY_PROBABLE".equals(dc.getCauseOfDeathBasis())
                || "FIELD_INVESTIGATION_PROBABLE".equals(dc.getCauseOfDeathBasis());
        if (!certified && !probable) {
            missing.add("signedCauseOfDeath");
        }
        return missing;
    }

    // ── community/field pathway helpers ──

    /** Default body-disposition context when the caller did not specify one. */
    private static String defaultBodyDisposition(String placeOfDeathContext, String sourceContext) {
        String place = placeOfDeathContext == null ? "" : placeOfDeathContext.toUpperCase();
        String source = sourceContext == null ? "" : sourceContext.toUpperCase();
        if (place.equals("INPATIENT") || place.equals("ED") || place.equals("THEATRE")
                || place.equals("BROUGHT_IN_DEAD") || place.equals("IN_TRANSIT")
                || source.equals("FACILITY") || source.equals("BROUGHT_IN_DEAD")
                || source.equals("INPATIENT_DISCHARGE")) {
            return "BROUGHT_TO_FACILITY";
        }
        return "NOT_BROUGHT_TO_FACILITY";
    }

    /** Map a field disposition type to the body-disposition context recorded on the case. */
    private static String dispositionToBodyContext(String dispositionType) {
        if (dispositionType == null) return "NOT_BROUGHT_TO_FACILITY";
        return switch (dispositionType) {
            case "SAFE_AND_DIGNIFIED_BURIAL" -> "FIELD_SAFE_BURIAL";
            case "FAMILY_RELEASE" -> "COMMUNITY_RELEASE";
            case "FUNERAL_DIRECTOR" -> "FUNERAL_DIRECTOR_DIRECT";
            case "POLICE_HANDOVER" -> "POLICE_CUSTODY";
            default -> "NOT_BROUGHT_TO_FACILITY";
        };
    }

    /** Merge freshly-surfaced medico-legal triggers into the existing comma list, de-duplicated. */
    private static String mergeTriggers(String existing, List<String> add) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        if (existing != null && !existing.isBlank()) {
            for (String t : existing.split(",")) if (!t.isBlank()) set.add(t.trim());
        }
        for (String t : add) if (t != null && !t.isBlank()) set.add(t.trim());
        return String.join(",", set);
    }

    /**
     * Link a supporting document reference to a death case. The binary is stored by the document
     * service (owner-routed); here we record the reference + type on the append-only trail so the
     * case carries provenance of attached evidence (e.g. ID photo, referral note).
     */
    @Transactional
    public DeathCaseEntity attachSupportingDocument(UUID caseId, String documentRef, String documentType) {
        TrustContext ctx = TrustContextHolder.require();
        DeathCaseEntity dc = require(ctx, caseId);
        audit(dc, "DOCUMENT_ATTACHED", "documentRef", documentType, documentRef,
                "Supporting document linked", ctx, roleOf(ctx));
        return dc;
    }

    // ── reads ──

    @Transactional(readOnly = true)
    public DeathCaseEntity getCase(UUID caseId) {
        return require(TrustContextHolder.require(), caseId);
    }

    @Transactional(readOnly = true)
    public List<DeathCaseEntity> listFacilityCases() {
        TrustContext ctx = TrustContextHolder.require();
        return deathCaseRepository.findByTenantIdAndFacilityIdOrderByCreatedAtDesc(ctx.tenantId(), ctx.facilityId());
    }

    @Transactional(readOnly = true)
    public List<DeathAuditEntity> auditTrail(UUID caseId) {
        return deathAuditRepository.findByCaseIdOrderByOccurredAtAsc(caseId);
    }

    @Transactional(readOnly = true)
    public BodyCustodyEntity getCustody(UUID caseId) {
        TrustContext ctx = TrustContextHolder.require();
        return bodyCustodyRepository.findByTenantIdAndCaseId(ctx.tenantId(), caseId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<VerbalAutopsyEntity> listVerbalAutopsies(UUID caseId) {
        TrustContext ctx = TrustContextHolder.require();
        return verbalAutopsyRepository.findByTenantIdAndCaseIdOrderByCreatedAtDesc(ctx.tenantId(), caseId);
    }

    @Transactional(readOnly = true)
    public List<FieldBodyManagementEntity> listFieldBodyManagement(UUID caseId) {
        TrustContext ctx = TrustContextHolder.require();
        return fieldBodyManagementRepository.findByTenantIdAndCaseIdOrderByCreatedAtDesc(ctx.tenantId(), caseId);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private DeathCaseEntity require(TrustContext ctx, UUID caseId) {
        return deathCaseRepository.findByTenantIdAndCaseId(ctx.tenantId(), caseId)
                .orElseThrow(() -> new IllegalArgumentException("Death case not found: " + caseId));
    }

    private static String roleOf(TrustContext ctx) {
        String r = ctx.actorType();
        return r == null ? "UNKNOWN" : r.toUpperCase();
    }

    private void audit(DeathCaseEntity dc, String eventType, String field, String oldVal,
                       String newVal, String reason, TrustContext ctx, String role) {
        DeathAuditEntity a = new DeathAuditEntity();
        a.setAuditId(UUID.randomUUID());
        a.setTenantId(ctx.tenantId());
        a.setCaseId(dc.getId());
        a.setEventType(eventType);
        a.setFieldChanged(field);
        a.setOldValue(oldVal);
        a.setNewValue(newVal);
        a.setReason(reason);
        a.setActorId(ctx.actorId());
        a.setActorRole(role);
        a.setFacilityId(dc.getFacilityId());
        a.setOccurredAt(OffsetDateTime.now());
        deathAuditRepository.save(a);
    }


    private void writeOutbox(String aggregateType, String aggregateId,
                             String eventType, String payloadJson) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payloadJson);
        outbox.setTenantId(TrustContextHolder.require().tenantId());
        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise payload: {}", e.getMessage());
            return "{}";
        }
    }
}
