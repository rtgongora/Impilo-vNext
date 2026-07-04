package zw.gov.mohcc.impilo.rtc.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Participant descriptor on token/provision requests. {@code mediaProfile}
 * is optional: FULL or AUDIO_ONLY; when omitted, roles whose template grant
 * sets audioOnlyDefault resolve to AUDIO_ONLY, everyone else to FULL.
 */
public record RtcParticipant(
        @NotBlank String identity,
        String displayName,
        @NotBlank String role,
        String mediaProfile
) {
    /** Backward-compatible constructor for callers without a media profile. */
    public RtcParticipant(String identity, String displayName, String role) {
        this(identity, displayName, role, null);
    }
}
