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
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.BodyCustodyRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.DeathAuditRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.DeathCaseRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
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
        writeOutbox("DEATH_CASE", dc.getId().toString(), "DEATH_CONFIRMED", toJson(payload));

        log.info("Death confirmed: case={}, journey={}, by role {}, coronerReferral={}",
                dc.getId(), dc.getJourneyId(), role, dc.isCoronerReferralRequired());
        return dc;
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

    /** Fields the CRVS package cannot be submitted without. */
    public List<String> crvsMissingFields(DeathCaseEntity dc) {
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        if (dc.getDeathDatetime() == null) missing.add("dateOfDeath");
        if (dc.getPatientCpid() == null && dc.getTemporaryIdentityRef() == null) missing.add("deceasedIdentity");
        if (dc.getPlaceOfDeathContext() == null) missing.add("placeOfDeath");
        if (!"SIGNED".equals(dc.getCertificationStatus()) && !"AMENDED".equals(dc.getCertificationStatus())) {
            missing.add("signedCauseOfDeath");
        }
        return missing;
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
