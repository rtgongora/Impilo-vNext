package zw.gov.mohcc.impilo.rtc.model;

import java.time.Instant;

/** One row of rtc.recordings — a single egress lifecycle for a session. */
public record RtcRecordingRecord(
        Long id,
        String sessionId,
        String egressId,
        String status,
        String layout,
        String storageBucket,
        String storageKey,
        String documentObjectId,
        Integer durationSeconds,
        Long sizeBytes,
        String startedBy,
        String startedByRole,
        String consentReference,
        Instant createdAt,
        Instant completedAt
) {
}
