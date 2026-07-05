package zw.gov.mohcc.impilo.rtc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionRecord;
import zw.gov.mohcc.impilo.rtc.model.RtcStreamStartRequest;
import zw.gov.mohcc.impilo.rtc.persistence.RtcSessionPersistence;
import zw.gov.mohcc.impilo.sessiontemplates.SessionMode;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplate;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplateRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RTMP stream-out lifecycle behind {@code rtc.gateway.stream-out-enabled}
 * (default false — HONEST SEAM: the estate has no restream target infra).
 *
 * <p>Role gating reuses the session template's RecordingRule {@code whoCanStart}:
 * pushing the room composite to an external RTMP target is at least as
 * sensitive as recording it, and the template's recording roles (LIVE_EVENT:
 * HOST/PRODUCER) are the governed capability vocabulary for that.
 *
 * <p>Egress state is not persisted yet — start returns the egressId, stop takes
 * it back. Durable stream-egress bookkeeping lands together with the restream
 * target infrastructure.
 */
@Service
public class RtcStreamOutService {

    static final String EVT_STREAM_REQUESTED = "impilo.rtc.stream.requested.v1";
    static final String EVT_STREAM_STOPPED = "impilo.rtc.stream.stopped.v1";

    private static final Logger log = LoggerFactory.getLogger(RtcStreamOutService.class);

    private final RtcGatewayProperties properties;
    private final RtcSessionPersistence sessions;
    private final LiveKitEgressClient egressClient;
    private final RtcOutboxPublisher outboxPublisher;
    private final SessionTemplateRegistry templates;

    public RtcStreamOutService(RtcGatewayProperties properties,
                               RtcSessionPersistence sessions,
                               LiveKitEgressClient egressClient,
                               RtcOutboxPublisher outboxPublisher,
                               SessionTemplateRegistry templates) {
        this.properties = properties;
        this.sessions = sessions;
        this.egressClient = egressClient;
        this.outboxPublisher = outboxPublisher;
        this.templates = templates;
    }

    public Map<String, Object> start(String sessionId, RtcStreamStartRequest request) {
        if (!properties.getGateway().isStreamOutEnabled()) {
            throw new IllegalStateException(
                    "RTMP stream-out is disabled on this gateway (rtc.gateway.stream-out-enabled=false)");
        }
        RtcSessionRecord session = sessions.findById(sessionId)
                .orElseThrow(() -> new RtcNotFoundException("RTC session not found"));
        if ("ENDED".equals(session.status())) {
            throw new IllegalArgumentException("Session has ended; stream-out cannot start");
        }
        List<String> urls = validateUrls(request.urls());

        SessionTemplate template = templateFor(session);
        SessionTemplate.RecordingRule rule = template.recording();
        if (rule == null || !rule.canStart(request.startedByRole())) {
            throw new IllegalArgumentException("Role " + request.startedByRole()
                    + " may not start a stream-out for session mode " + template.sessionMode());
        }

        String layout = request.layout() == null || request.layout().isBlank()
                ? template.defaultLayout()
                : request.layout();
        LiveKitEgressClient.EgressStart start =
                egressClient.startRoomCompositeStream(session.roomName(), layout, urls);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.id());
        payload.put("sessionMode", session.sessionMode());
        payload.put("owningService", session.owningService());
        payload.put("owningRef", session.owningRef());
        payload.put("roomName", session.roomName());
        payload.put("egressId", start.egressId());
        payload.put("layout", layout);
        payload.put("startedBy", request.startedBy());
        payload.put("startedByRole", request.startedByRole());
        payload.put("targetCount", urls.size());
        outboxPublisher.append("RtcSession", session.id(), EVT_STREAM_REQUESTED, payload);

        log.info("Stream-out requested for session {} (egress {}, {} target(s))",
                sessionId, start.egressId(), urls.size());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", session.id());
        out.put("egressId", start.egressId());
        out.put("layout", layout);
        out.put("targetCount", urls.size());
        out.put("status", "REQUESTED");
        return out;
    }

    /** Stop is not gated on the flag so an in-flight stream can always be torn down. */
    public Map<String, Object> stop(String sessionId, String egressId) {
        RtcSessionRecord session = sessions.findById(sessionId)
                .orElseThrow(() -> new RtcNotFoundException("RTC session not found"));
        if (egressId == null || egressId.isBlank()) {
            throw new IllegalArgumentException("egressId is required to stop a stream-out");
        }
        egressClient.stopEgress(egressId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", session.id());
        payload.put("owningService", session.owningService());
        payload.put("owningRef", session.owningRef());
        payload.put("egressId", egressId);
        outboxPublisher.append("RtcSession", session.id(), EVT_STREAM_STOPPED, payload);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sessionId", session.id());
        out.put("egressId", egressId);
        out.put("status", "STOP_REQUESTED");
        return out;
    }

    private List<String> validateUrls(List<String> urls) {
        List<String> normalized = urls == null ? List.of() : urls.stream()
                .filter(u -> u != null && !u.isBlank())
                .map(String::trim)
                .toList();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one rtmp:// or rtmps:// target URL is required");
        }
        for (String url : normalized) {
            String lower = url.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("rtmp://") && !lower.startsWith("rtmps://")) {
                throw new IllegalArgumentException("Stream target must be rtmp:// or rtmps:// — got: " + url);
            }
        }
        return normalized;
    }

    private SessionTemplate templateFor(RtcSessionRecord session) {
        SessionMode mode = SessionMode.fromWire(session.sessionMode());
        if (mode == null) {
            mode = SessionMode.TELEMEDICINE;
        }
        try {
            return templates.get(mode);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Session template unavailable for mode " + mode + " — stream-out refused", ex);
        }
    }
}
