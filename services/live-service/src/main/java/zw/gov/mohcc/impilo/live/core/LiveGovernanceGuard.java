package zw.gov.mohcc.impilo.live.core;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.live.domain.LiveMode;
import zw.gov.mohcc.impilo.live.domain.OwningService;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;

import java.util.Set;

/**
 * Central Impilo Live governance enforcement (doctrine §8).
 *
 * <p>Impilo Live is the governed live engagement layer of the Impilo Health OS — clinical sessions
 * are stricter than public broadcasts. This guard is the single place where doctrine is enforced so
 * that every entry point (legacy create, the typed scheduling endpoints, joins and recording) obeys
 * the same rules. It is intentionally free of I/O so it is fully unit-testable; consent/identity
 * facts are passed in by callers (which resolve them via Tshepo / consent services).
 */
@Component
public class LiveGovernanceGuard {

    private static final Set<String> PUBLIC_VISIBILITIES = Set.of("PUBLIC");
    private static final Set<String> PERMISSIVE_RECORDING = Set.of("ALLOWED", "POLICY_PERMITTED");
    private static final Set<String> CLINICAL_JOIN_ROLES =
            Set.of("HOST", "COHOST", "PRESENTER", "MODERATOR", "ATTENDEE", "INTERPRETER", "SUPPORT", "ADMIN");

    /**
     * Normalises governed defaults for a mode and rejects any event that would violate doctrine.
     * Must run before an event is persisted by any creation path.
     */
    public void validateForCreate(LiveEventEntity event) {
        LiveMode mode = parseMode(event.getMode());
        OwningService owner = parseOwner(event.getOwningService());

        applyDefaults(event, mode);

        // Doctrine: no orphan live events. An owning service (other than standalone) needs its workflow id.
        if (owner.requiresOwningEntity() && isBlank(event.getOwningEntityId())) {
            throw LiveGovernanceException.invalid(
                    "MISSING_OWNING_ENTITY",
                    "owningEntityId is required when owningService is " + owner
                            + " — Impilo Live does not create orphan events for owned workflows.");
        }

        switch (mode) {
            case CLINICAL_SESSION -> validateClinical(event, owner);
            case PUBLIC_BROADCAST, EMERGENCY_BRIEFING -> validatePublicBroadcast(event, mode);
            case WEBINAR_CPD -> validateWebinar(event, owner);
            default -> { /* professional meeting / hybrid: baseline checks only */ }
        }

        validateRecordingConsistency(event);
    }

    private void validateClinical(LiveEventEntity event, OwningService owner) {
        // Clinical care is owned by Telemedicine / PCT — never by Impilo Live itself.
        if (owner != OwningService.TELEMEDICINE) {
            throw LiveGovernanceException.invalid(
                    "CLINICAL_OWNER_REQUIRED",
                    "Clinical sessions must be owned by TELEMEDICINE; Impilo Live owns live capability, not clinical care.");
        }
        // No free-floating clinical video rooms (doctrine §2): clinical context + identities mandatory.
        if (isBlank(event.getClinicalContextRef())) {
            throw LiveGovernanceException.invalid(
                    "CLINICAL_CONTEXT_REQUIRED",
                    "A clinical session requires an appointment/encounter context (clinicalContextRef).");
        }
        if (isBlank(event.getClientId()) || isBlank(event.getProviderId())) {
            throw LiveGovernanceException.invalid(
                    "CLINICAL_IDENTITIES_REQUIRED",
                    "A clinical session requires both clientId and providerId.");
        }
        if (isBlank(event.getFacilityId())) {
            throw LiveGovernanceException.invalid(
                    "CLINICAL_FACILITY_REQUIRED",
                    "A clinical session requires a facility context (facilityId).");
        }
        // Consent-aware: clinical sessions must require consent.
        if (!"REQUIRED".equals(event.getConsentPolicy())) {
            throw LiveGovernanceException.invalid(
                    "CLINICAL_CONSENT_REQUIRED",
                    "Clinical sessions must set consentPolicy=REQUIRED.");
        }
        // Patient-identifiable clinical recordings disabled by default unless policy explicitly permits.
        if (event.isRecordingAllowed() && !PERMISSIVE_RECORDING.contains(event.getRecordingPolicy())) {
            throw LiveGovernanceException.invalid(
                    "CLINICAL_RECORDING_NOT_PERMITTED",
                    "Clinical recordings are disabled by default; set an explicit recordingPolicy to permit them.");
        }
        // Clinical sessions are never publicly visible.
        if (isPublicVisibility(event)) {
            throw LiveGovernanceException.invalid(
                    "CLINICAL_VISIBILITY_INVALID",
                    "Clinical sessions cannot be public; use a restricted/private visibility.");
        }
    }

    private void validatePublicBroadcast(LiveEventEntity event, LiveMode mode) {
        if (mode.moderationRequired() && (isBlank(event.getModerationPolicy()) || "NONE".equals(event.getModerationPolicy()))) {
            throw LiveGovernanceException.invalid(
                    "BROADCAST_MODERATION_REQUIRED",
                    "Public broadcasts and emergency briefings must be moderated (moderationPolicy != NONE).");
        }
        if (mode.speakerVerificationRequired() && !event.isSpeakerVerificationRequired()) {
            throw LiveGovernanceException.invalid(
                    "BROADCAST_SPEAKER_VERIFICATION_REQUIRED",
                    "Public broadcasts must verify official speakers (speakerVerificationRequired=true).");
        }
    }

    private void validateWebinar(LiveEventEntity event, OwningService owner) {
        // CPD learning truth is owned by Fundo: a Fundo-owned CPD webinar must be linked to a course.
        if (owner == OwningService.FUNDO
                && event.isCpdEnabled()
                && event.getLinkedFundoCourseId() == null
                && isBlank(event.getOwningEntityId())) {
            throw LiveGovernanceException.invalid(
                    "WEBINAR_COURSE_LINK_REQUIRED",
                    "A CPD-bearing webinar must be linked to a Fundo course (linkedFundoCourseId or owningEntityId).");
        }
    }

    private void validateRecordingConsistency(LiveEventEntity event) {
        if (event.isRecordingAllowed() && "DISABLED".equals(event.getRecordingPolicy())) {
            throw LiveGovernanceException.invalid(
                    "RECORDING_POLICY_CONFLICT",
                    "recordingAllowed=true conflicts with recordingPolicy=DISABLED.");
        }
        if (event.isReplayAllowed() && "DISABLED".equals(event.getReplayPolicy())) {
            // Replay implies a recording/replay artifact exists; keep policy honest.
            event.setReplayPolicy("ALLOWED");
        }
    }

    /**
     * Enforce access at join time. Callers resolve consent/identity facts (via Tshepo / consent) and
     * pass them in. Throws if the participant must not enter this session.
     */
    public void validateJoin(LiveEventEntity event, String role, String participantType, boolean consentGranted) {
        LiveMode mode = parseMode(event.getMode());
        boolean publicParticipant = isPublicParticipant(participantType);

        // Public/anonymous/citizen participants may only enter publicly-visible events.
        if (publicParticipant && !isPublicVisibility(event)) {
            throw LiveGovernanceException.forbidden(
                    "RESTRICTED_EVENT_PUBLIC_DENIED",
                    "This session is restricted to authorised participants and cannot be joined publicly.");
        }

        if (mode == LiveMode.CLINICAL_SESSION) {
            if (publicParticipant) {
                throw LiveGovernanceException.forbidden(
                        "CLINICAL_PUBLIC_DENIED",
                        "Clinical sessions cannot be joined by public participants.");
            }
            String normalisedRole = role == null ? "ATTENDEE" : role.trim().toUpperCase();
            if (!CLINICAL_JOIN_ROLES.contains(normalisedRole)) {
                throw LiveGovernanceException.forbidden(
                        "CLINICAL_ROLE_DENIED",
                        "Role " + normalisedRole + " is not permitted in a clinical session.");
            }
            if ("REQUIRED".equals(event.getConsentPolicy()) && !consentGranted) {
                throw LiveGovernanceException.forbidden(
                        "CLINICAL_CONSENT_NOT_GRANTED",
                        "Consent has not been granted for this clinical session.");
            }
        }
    }

    /** Enforce recording governance at the moment recording is requested. */
    public void validateRecordingRequest(LiveEventEntity event) {
        if (!event.isRecordingAllowed() || "DISABLED".equals(event.getRecordingPolicy())) {
            throw LiveGovernanceException.forbidden(
                    "RECORDING_NOT_PERMITTED",
                    "Recording is not permitted for this event under its governance policy.");
        }
    }

    // ---- normalisation & helpers ----

    private void applyDefaults(LiveEventEntity event, LiveMode mode) {
        if (isBlank(event.getMode())) {
            event.setMode(mode.name());
        }
        if (isBlank(event.getOwningService())) {
            event.setOwningService(OwningService.STANDALONE_IMPILO_LIVE.name());
        }
        if (isBlank(event.getContextType())) {
            event.setContextType(mode.defaultContextType().name());
        }
        if (isBlank(event.getRecordingPolicy())) {
            event.setRecordingPolicy(mode.recordingAllowedByDefault() ? "ALLOWED" : "DISABLED");
        }
        if (isBlank(event.getReplayPolicy())) {
            event.setReplayPolicy(event.isReplayAllowed() ? "ALLOWED" : "DISABLED");
        }
        if (isBlank(event.getConsentPolicy())) {
            event.setConsentPolicy(mode.clinicalContextRequired() ? "REQUIRED" : "NOT_REQUIRED");
        }
        if (isBlank(event.getModerationPolicy())) {
            event.setModerationPolicy(mode.moderationRequired() ? "MODERATED" : "NONE");
        }
        // Public-facing official communications default to moderated + verified speakers (safe defaults,
        // not silent rejection of legacy callers).
        if (mode.moderationRequired() && "NONE".equals(event.getModerationPolicy())) {
            event.setModerationPolicy("MODERATED");
        }
        if (mode.speakerVerificationRequired()) {
            event.setSpeakerVerificationRequired(true);
        }
        if (mode == LiveMode.EMERGENCY_BRIEFING) {
            event.setEmergencyFlag(true);
        }
        // Clinical recordings are forced off by default (never silently on).
        if (mode == LiveMode.CLINICAL_SESSION && !PERMISSIVE_RECORDING.contains(event.getRecordingPolicy())) {
            event.setRecordingAllowed(false);
            event.setRecordingPolicy("DISABLED");
        }
        if ("REQUIRED".equals(event.getConsentPolicy())) {
            event.setConsentRequired(true);
        }
    }

    private boolean isPublicVisibility(LiveEventEntity event) {
        return event.getVisibility() != null && PUBLIC_VISIBILITIES.contains(event.getVisibility().toUpperCase());
    }

    private boolean isPublicParticipant(String participantType) {
        if (participantType == null || participantType.isBlank()) {
            return true;
        }
        String pt = participantType.trim().toUpperCase();
        return pt.equals("PUBLIC") || pt.equals("ANONYMOUS") || pt.equals("CITIZEN") || pt.equals("GUEST");
    }

    private LiveMode parseMode(String raw) {
        LiveMode mode = LiveMode.fromString(raw);
        if (mode == null) {
            throw LiveGovernanceException.invalid("INVALID_MODE", "A valid Impilo Live mode is required.");
        }
        return mode;
    }

    private OwningService parseOwner(String raw) {
        OwningService owner = OwningService.fromString(raw);
        return owner == null ? OwningService.STANDALONE_IMPILO_LIVE : owner;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
