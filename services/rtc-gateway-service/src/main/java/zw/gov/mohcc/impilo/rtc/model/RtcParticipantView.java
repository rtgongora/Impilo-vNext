package zw.gov.mohcc.impilo.rtc.model;

import java.time.Instant;

/** Lobby participant row as exposed on GET /sessions/{id}/participants. */
public record RtcParticipantView(
        String identity,
        String displayName,
        String role,
        String state,
        String mediaProfile,
        Instant requestedAt,
        Instant admittedAt,
        String admittedBy,
        String deniedReason
) {
    public static RtcParticipantView of(RtcParticipantRecord record) {
        return new RtcParticipantView(
                record.identity(),
                record.displayName(),
                record.role(),
                record.state(),
                record.mediaProfile(),
                record.requestedAt(),
                record.admittedAt(),
                record.admittedBy(),
                record.deniedReason());
    }
}
