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
 * purpose) but cannot evaluate subject-level relationship for a POST-to-collection write — the
 * subject CPID is in the request body, not the path, so the PDP has no resource id to bind to (it
 * explicitly delegates this to the owning service). The platform's baseline access model is
 * <em>facility-team-level</em> (role + facility + purpose), so a {@code subject_cpid}-only write is
 * permitted by RBAC. This guard adds the delegated <strong>verify-when-present</strong> consistency
 * control: when a caller supplies a care-context reference (a journey or encounter), that context
 * must exist in this tenant and belong to the write's subject. This closes the cross-patient case —
 * a clinician writing for patient A while referencing patient B's journey/encounter — without
 * imposing a stronger-than-platform subject-relationship requirement on context-free writes.</p>
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
     * Verify-when-present: when {@code ctx}'s caller references a care context (journey or encounter)
     * for {@code subjectCpid}, that context must resolve (in tenant) to the subject. A write with no
     * care-context reference is permitted (facility-team-level RBAC is the control). Throws
     * {@code 403 FORBIDDEN} when a supplied context does not belong to the subject.
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

        boolean hasContext = (encounterId != null && !encounterId.isBlank())
                || (journeyId != null && !journeyId.isBlank());
        if (!hasContext) {
            return; // no care-context reference to verify (facility-team-level RBAC is the control)
        }

        // Emergency / break-glass: never block clinical care. Waiver is audited upstream.
        String purpose = ctx.purposeOfUse() == null ? "" : ctx.purposeOfUse().toUpperCase(Locale.ROOT);
        if (purpose.equals("EMERGENCY") || purpose.equals("BREAK_GLASS")) {
            log.warn("CARE-RELATIONSHIP WAIVED (purpose={}): actor={} writing for subject={} with an "
                            + "unverified care context — emergency/break-glass override.",
                    purpose, ctx.actorId(), subjectCpid);
            return;
        }

        // A context was supplied — it must resolve to the subject (encounter first, then journey).
        if (verifyEncounter(ctx, subjectCpid, encounterId) || verifyJourney(ctx, subjectCpid, journeyId)) {
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Referenced care context does not belong to the subject of this clinical write.");
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
