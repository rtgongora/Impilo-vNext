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
 * <p><strong>Emergency care is never blocked:</strong> under {@code EMERGENCY} or {@code BREAK_GLASS}
 * purpose-of-use the relationship requirement is waived (with an elevated-visibility log), mirroring
 * the platform's break-glass principle. The waiver is still fully audited upstream.</p>
 */
@Component
public class ClinicalAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(ClinicalAccessGuard.class);

    private final JourneyRepository journeyRepository;
    private final EncounterRepository encounterRepository;

    public ClinicalAccessGuard(JourneyRepository journeyRepository,
                               EncounterRepository encounterRepository) {
        this.journeyRepository = journeyRepository;
        this.encounterRepository = encounterRepository;
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

        // Emergency / break-glass: never block clinical care, even with no care context. Audited upstream.
        String purpose = ctx.purposeOfUse() == null ? "" : ctx.purposeOfUse().toUpperCase(Locale.ROOT);
        if (purpose.equals("EMERGENCY") || purpose.equals("BREAK_GLASS")) {
            log.warn("CARE-RELATIONSHIP WAIVED (purpose={}): actor={} writing for subject={} without a "
                            + "verified care context — emergency/break-glass override.",
                    purpose, ctx.actorId(), subjectCpid);
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
