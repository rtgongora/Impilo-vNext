package zw.gov.mohcc.impilo.rtc.model;

import java.time.Instant;
import java.util.Map;

public record RtcSessionRecord(
        String id,
        String tenantId,
        String provider,
        String roomName,
        String roomUrl,
        String status,
        String sessionMode,
        String owningService,
        String owningRef,
        String patientId,
        String providerId,
        String encounterId,
        String referralId,
        String consentReference,
        String channel,
        Map<String, Boolean> capabilities,
        Map<String, Object> mediaPolicy,
        Instant createdAt,
        Instant updatedAt
) {
    public RtcSessionRecord withStatus(String nextStatus) {
        return new RtcSessionRecord(
                id, tenantId, provider, roomName, roomUrl, nextStatus,
                sessionMode, owningService, owningRef,
                patientId, providerId, encounterId, referralId, consentReference, channel,
                capabilities, mediaPolicy, createdAt, Instant.now());
    }
}
