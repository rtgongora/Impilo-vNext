package zw.gov.mohcc.impilo.pct.core.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.sharedkernel.security.BreakGlassGrantCheck;
import zw.gov.mohcc.impilo.sharedkernel.security.BreakGlassGrantClient;
import zw.gov.mohcc.impilo.sharedkernel.security.BreakGlassGrantQuery;
import zw.gov.mohcc.impilo.sharedkernel.security.EmergencyAccessGuard;
import zw.gov.mohcc.impilo.sharedkernel.security.UngovernedOverride;
import zw.gov.mohcc.impilo.sharedkernel.security.UngovernedOverrideRecorder;

import java.util.Locale;
import java.util.Optional;

/**
 * Enforces the <strong>subject-relationship</strong> dimension of the access model for clinical
 * writes (the 10-dimension doctrine, dimension 6 — "subject relationship").
 *
 * <p>Envoy ext_authz / TSHEPO enforces the RBAC dimensions (actor type, clinical role, facility,
 * purpose) but cannot evaluate subject-level consent/relationship for a POST-to-collection write —
 * the subject CPID is in the request body, not the path, so the PDP has no resource id to bind to
 * (it explicitly delegates this to the owning service). This guard is that delegated control: a
 * clinical record may only be written for a patient the acting provider holds an <em>active care
 * context</em> with — i.e. a journey or encounter (in this tenant) whose patient is the write's
 * subject. A write that references no resolvable care context is <strong>rejected</strong>
 * (403); this stops a clinician with the coarse RBAC from minting clinical records against an
 * arbitrary CPID supplied in the body.</p>
 *
 * <h2>The emergency waiver, and what it now costs to obtain</h2>
 *
 * <p>Under {@code EMERGENCY} or {@code BREAK_GLASS} purpose-of-use the relationship requirement is
 * waived. Until Phase 0 workstream A2 that waiver was granted on the purpose string alone — and
 * purpose-of-use is client-supplied on this path, so any caller able to set
 * {@code X-Purpose-Of-Use: BREAK_GLASS} could mint clinical records against an arbitrary CPID, which
 * is exactly what the strict branch exists to prevent. The javadoc's claim that "the waiver is still
 * fully audited upstream" was false: no upstream decision was made and none was recorded.</p>
 *
 * <p>The waiver is now granted only when the trust plane confirms an active break-glass grant, or
 * cannot be reached. Per the PO ruling of 2026-08-07:</p>
 *
 * <ul>
 *   <li><b>Valid grant</b> — waived, as before.</li>
 *   <li><b>No grant</b> — <b>refused</b> (403), the same as any other write with no care context.</li>
 *   <li><b>Trust plane unreachable</b> — waived, but only after an ungoverned override is durably
 *       recorded with a named post-hoc review obligation.</li>
 * </ul>
 *
 * <p>Emergency care is still never blocked by a policy-service outage. It is now blocked by the
 * absence of an emergency.</p>
 */
@Component
public class ClinicalAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(ClinicalAccessGuard.class);

    private final JourneyRepository journeyRepository;
    private final EncounterRepository encounterRepository;
    private final BreakGlassGrantClient grantClient;
    private final UngovernedOverrideRecorder overrideRecorder;

    public ClinicalAccessGuard(JourneyRepository journeyRepository,
                               EncounterRepository encounterRepository,
                               BreakGlassGrantClient grantClient,
                               UngovernedOverrideRecorder overrideRecorder) {
        this.journeyRepository = journeyRepository;
        this.encounterRepository = encounterRepository;
        this.grantClient = grantClient;
        this.overrideRecorder = overrideRecorder;
    }

    /**
     * Require that {@code ctx}'s actor holds an active care context (journey or encounter) for
     * {@code subjectCpid}. Throws {@code 403 FORBIDDEN} when no verifiable relationship exists —
     * including when no care context is referenced at all. Waived under EMERGENCY/BREAK_GLASS.
     *
     * @param ctx         the trust context (tenant + purpose-of-use)
     * @param subjectCpid the patient the write targets (must be non-blank)
     * @param journeyId   the referenced journey id, or {@code null}
     * @param encounterId the referenced encounter id, or {@code null}
     */
    public void requireCareRelationship(TrustContext ctx, String subjectCpid,
                                        String journeyId, String encounterId) {
        if (subjectCpid == null || subjectCpid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "subject_cpid is required");
        }

        // Emergency / break-glass: the purpose selects this path, the grant decides it.
        String purpose = ctx.purposeOfUse() == null ? "" : ctx.purposeOfUse().toUpperCase(Locale.ROOT);
        if (purpose.equals("EMERGENCY") || purpose.equals("BREAK_GLASS")) {
            evaluateBreakGlassWaiver(ctx, subjectCpid, purpose);
            return;
        }

        // Strict: the write must reference a journey or encounter that resolves to the subject.
        if (verifyEncounter(ctx, subjectCpid, encounterId) || verifyJourney(ctx, subjectCpid, journeyId)) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "No active care context for this patient: a clinical write must reference a journey "
                        + "or encounter that belongs to the subject (or be performed under emergency purpose).");
    }

    /**
     * Decide the break-glass waiver against the real grant model. Returns normally when the write
     * may proceed; throws 403 when the trust plane reports no grant.
     *
     * <p>The UNREACHABLE branch records <em>before</em> it returns, and does not catch what the
     * recorder throws. A waiver granted with no trace is worse than a refusal — it is the
     * ungoverned override this method exists to make visible, happening invisibly.</p>
     */
    private void evaluateBreakGlassWaiver(TrustContext ctx, String subjectCpid, String purpose) {
        BreakGlassGrantQuery query = new BreakGlassGrantQuery(
                ctx.tenantId() == null ? null : ctx.tenantId().toString(),
                ctx.actorId(),
                null, // pct receives no escalation grant token; the actor-scoped check answers here
                purpose,
                "CLINICAL_WRITE_WITHOUT_CARE_CONTEXT",
                subjectCpid);

        BreakGlassGrantCheck check = grantClient.check(query);

        switch (check.verdict()) {
            case VALID -> log.warn("CARE-RELATIONSHIP WAIVED (purpose={}, grant validated): actor={} writing "
                            + "for subject={} without a verified care context — governed break-glass override, "
                            + "grantSource={} grantRef={}.",
                    purpose, ctx.actorId(), subjectCpid, check.source(), check.grantRef());

            case NO_GRANT -> {
                log.warn("CARE-RELATIONSHIP DENY (purpose={}, no grant): actor={} attempted a clinical write "
                                + "for subject={} with no care context and no active break-glass grant.",
                        purpose, ctx.actorId(), subjectCpid);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "An emergency purpose-of-use does not by itself waive the care-relationship "
                                + "requirement: no active break-glass grant is held by this actor. Request one "
                                + "via POST /v1/break-glass, which records the clinical reason and schedules "
                                + "supervisor review.");
            }

            case UNREACHABLE -> {
                UngovernedOverride override = UngovernedOverride.from(query, check);
                overrideRecorder.record(override);
                log.warn("{}: purpose={} actor={} subject={} reason={} — the break-glass grant could NOT be "
                                + "checked. The care-relationship requirement was WAIVED under the "
                                + "emergency-care ruling and is UNVALIDATED. {}",
                        EmergencyAccessGuard.UNGOVERNED_OVERRIDE_MARKER, purpose, ctx.actorId(), subjectCpid,
                        check.detail(), override.reviewObligation());
            }

            default -> throw new IllegalStateException("Unhandled grant verdict: " + check.verdict());
        }
    }

    /**
     * @return {@code true} if a non-blank, parseable encounter id resolves (in tenant) to the subject;
     *         throws 403 on a found-but-mismatched encounter; {@code false} when no usable encounter
     *         reference was supplied (so the caller can fall through to the journey check).
     */
    private boolean verifyEncounter(TrustContext ctx, String subjectCpid, String encounterId) {
        if (encounterId == null || encounterId.isBlank()) {
            return false;
        }
        Long id;
        try {
            id = Long.valueOf(encounterId.trim());
        } catch (NumberFormatException e) {
            return false; // not a usable encounter ref — let the journey check decide
        }
        Optional<EncounterEntity> enc = encounterRepository.findByTenantIdAndId(ctx.tenantId(), id);
        if (enc.isEmpty()) {
            return false; // unknown encounter — fall through (journey may still establish the relationship)
        }
        if (subjectCpid.equals(enc.get().getSubjectCpid())) {
            return true;
        }
        log.warn("CARE-RELATIONSHIP DENY: actor={} referenced encounter={} (subject={}) but wrote for subject={}",
                ctx.actorId(), id, enc.get().getSubjectCpid(), subjectCpid);
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Referenced encounter does not belong to the subject of this clinical write.");
    }

    private boolean verifyJourney(TrustContext ctx, String subjectCpid, String journeyId) {
        if (journeyId == null || journeyId.isBlank()) {
            return false;
        }
        Optional<JourneyEntity> journey = journeyRepository.findByTenantIdAndJourneyId(ctx.tenantId(), journeyId.trim());
        if (journey.isEmpty()) {
            return false;
        }
        if (subjectCpid.equals(journey.get().getPatientCpid())) {
            return true;
        }
        log.warn("CARE-RELATIONSHIP DENY: actor={} referenced journey={} (patient={}) but wrote for subject={}",
                ctx.actorId(), journeyId, journey.get().getPatientCpid(), subjectCpid);
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Referenced journey does not belong to the subject of this clinical write.");
    }
}
