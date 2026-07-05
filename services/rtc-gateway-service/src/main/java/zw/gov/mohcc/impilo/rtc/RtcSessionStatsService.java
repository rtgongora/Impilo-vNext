package zw.gov.mohcc.impilo.rtc;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionRecord;
import zw.gov.mohcc.impilo.rtc.persistence.RtcSessionPersistence;
import zw.gov.mohcc.impilo.rtc.persistence.RtcTelemetryPersistence;
import zw.gov.mohcc.impilo.rtc.persistence.RtcTelemetryPersistence.ParticipantStatRow;
import zw.gov.mohcc.impilo.rtc.persistence.RtcTelemetryPersistence.SessionLifecycle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Media-quality aggregates for one session, derived from the webhook-fed
 * {@code rtc.participant_stats} telemetry. This is the internal seam that
 * owning services (live-service analytics) consume — cross-database reads of
 * the rtc schema are forbidden, so rtc-gateway aggregates its own truth.
 *
 * <p>Honesty note: LiveKit webhooks carry join/leave/duration/disconnect-reason
 * facts. {@code connection_quality} exists as a column but no webhook populates
 * it (LiveKit emits no quality webhook), so quality-score aggregates are only
 * included when rows actually carry a value.
 */
@Service
public class RtcSessionStatsService {

    /** Disconnect reasons that indicate an orderly leave rather than a media failure. */
    private static final List<String> NORMAL_DISCONNECTS = List.of("CLIENT_INITIATED", "ROOM_DELETED");

    private final RtcSessionPersistence sessions;
    private final RtcTelemetryPersistence telemetry;

    public RtcSessionStatsService(RtcSessionPersistence sessions, RtcTelemetryPersistence telemetry) {
        this.sessions = sessions;
        this.telemetry = telemetry;
    }

    public Map<String, Object> stats(String sessionId) {
        RtcSessionRecord session = sessions.findById(sessionId)
                .orElseThrow(() -> new RtcNotFoundException("RTC session not found"));
        List<ParticipantStatRow> rows = telemetry.findParticipantStats(sessionId);

        long totalJoins = rows.size();
        long distinctParticipants = rows.stream()
                .map(ParticipantStatRow::identity)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long activeParticipants = rows.stream().filter(r -> r.joinedAt() != null && r.leftAt() == null).count();
        List<Integer> durations = rows.stream()
                .map(ParticipantStatRow::durationSeconds)
                .filter(Objects::nonNull)
                .toList();
        double avgDuration = durations.stream().mapToInt(Integer::intValue).average().orElse(0);
        int maxDuration = durations.stream().mapToInt(Integer::intValue).max().orElse(0);

        Map<String, Long> disconnectReasons = new TreeMap<>();
        long abnormalDisconnects = 0;
        for (ParticipantStatRow row : rows) {
            if (row.disconnectReason() == null || row.disconnectReason().isBlank()) {
                continue;
            }
            String reason = row.disconnectReason().trim().toUpperCase(Locale.ROOT);
            disconnectReasons.merge(reason, 1L, Long::sum);
            if (!NORMAL_DISCONNECTS.contains(reason)) {
                abnormalDisconnects++;
            }
        }
        Map<String, Long> qualityCounts = new TreeMap<>();
        for (ParticipantStatRow row : rows) {
            if (row.connectionQuality() != null && !row.connectionQuality().isBlank()) {
                qualityCounts.merge(row.connectionQuality().trim().toUpperCase(Locale.ROOT), 1L, Long::sum);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", session.id());
        out.put("sessionMode", session.sessionMode());
        out.put("owningService", session.owningService());
        out.put("owningRef", session.owningRef());
        out.put("totalJoins", totalJoins);
        out.put("distinctParticipants", distinctParticipants);
        out.put("activeParticipants", activeParticipants);
        out.put("avgDurationSeconds", Math.round(avgDuration * 10.0) / 10.0);
        out.put("maxDurationSeconds", maxDuration);
        out.put("abnormalDisconnects", abnormalDisconnects);
        out.put("disconnectReasons", disconnectReasons);
        if (!qualityCounts.isEmpty()) {
            out.put("connectionQuality", qualityCounts);
        }
        SessionLifecycle lifecycle = telemetry.findSessionLifecycle(sessionId).orElse(null);
        if (lifecycle != null) {
            out.put("startedAt", lifecycle.startedAt() == null ? null : lifecycle.startedAt().toString());
            out.put("endedAt", lifecycle.endedAt() == null ? null : lifecycle.endedAt().toString());
            if (lifecycle.peakParticipants() != null) {
                out.put("peakParticipants", lifecycle.peakParticipants());
            }
        }
        return out;
    }
}
