package zw.gov.mohcc.impilo.live.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.live.integration.FundoClient;
import zw.gov.mohcc.impilo.live.integration.MadiClient;
import zw.gov.mohcc.impilo.live.integration.NotificationClient;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cross-service orchestration for Impilo Live: notifications, Fundo CPD, Madi drives.
 */
@Service
public class LiveIntegrationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(LiveIntegrationOrchestrator.class);

    private final NotificationClient notificationClient;
    private final FundoClient fundoClient;
    private final MadiClient madiClient;

    public LiveIntegrationOrchestrator(NotificationClient notificationClient,
                                       FundoClient fundoClient,
                                       MadiClient madiClient) {
        this.notificationClient = notificationClient;
        this.fundoClient = fundoClient;
        this.madiClient = madiClient;
    }

    public void notifyRegistration(LiveEventEntity event, String participantId) {
        TrustContext ctx = trustOrNull();
        if (ctx == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel", "IN_APP");
        payload.put("to", participantId);
        payload.put("templateKey", "live.event.registration.confirmed");
        payload.put("variables", Map.of(
                "eventTitle", event.getTitle(),
                "eventId", event.getId().toString(),
                "startTime", event.getStartTime() != null ? event.getStartTime().toString() : ""
        ));
        notificationClient.sendNotification(ctx, payload);
    }

    public void notifyEventScheduled(LiveEventEntity event) {
        TrustContext ctx = trustOrNull();
        if (ctx == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel", "IN_APP");
        payload.put("to", event.getOrganiserId());
        payload.put("templateKey", "live.event.scheduled");
        payload.put("variables", Map.of(
                "eventTitle", event.getTitle(),
                "eventId", event.getId().toString()
        ));
        notificationClient.sendNotification(ctx, payload);
    }

    public void recordFundoCpdCompletion(LiveEventEntity event, String participantId) {
        if (!event.isCpdEnabled() || event.getLinkedFundoCourseId() == null) {
            return;
        }
        TrustContext ctx = trustOrNull();
        if (ctx == null) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("courseId", event.getLinkedFundoCourseId().toString());
        body.put("subjectType", "PROVIDER");
        body.put("subjectId", participantId);
        body.put("source", "IMPILO_LIVE");
        body.put("liveEventId", event.getId().toString());
        body.put("cpdPoints", event.getCpdPoints());
        fundoClient.recordLiveCompletion(ctx, body);
    }

    public Map<String, Object> resolveMadiDrive(LiveEventEntity event) {
        if (event.getLinkedMadiDriveId() == null) {
            return Map.of();
        }
        TrustContext ctx = trustOrNull();
        if (ctx == null) {
            return Map.of("driveId", event.getLinkedMadiDriveId().toString());
        }
        Map<String, Object> drive = madiClient.getDrive(ctx, event.getLinkedMadiDriveId().toString());
        if (drive.isEmpty()) {
            return Map.of("driveId", event.getLinkedMadiDriveId().toString(), "resolved", false);
        }
        Map<String, Object> out = new LinkedHashMap<>(drive);
        out.put("resolved", true);
        return out;
    }

    private TrustContext trustOrNull() {
        try {
            return TrustContextHolder.get();
        } catch (IllegalStateException ex) {
            log.debug("No trust context for live integration: {}", ex.getMessage());
            return null;
        }
    }
}
