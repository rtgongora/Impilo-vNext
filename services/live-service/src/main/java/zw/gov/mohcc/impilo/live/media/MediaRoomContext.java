package zw.gov.mohcc.impilo.live.media;

import java.util.Map;
import java.util.UUID;

public record MediaRoomContext(
        UUID tenantId,
        UUID eventId,
        UUID sessionId,
        String participantId,
        String participantRole,
        String facilityId,
        String providerRoomId,
        String providerType,
        Map<String, Object> attributes
) {
}
