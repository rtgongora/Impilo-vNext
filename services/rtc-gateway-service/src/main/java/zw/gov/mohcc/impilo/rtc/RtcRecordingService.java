package zw.gov.mohcc.impilo.rtc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.rtc.model.RtcRecordingRecord;
import zw.gov.mohcc.impilo.rtc.model.RtcRecordingStartRequest;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionRecord;
import zw.gov.mohcc.impilo.rtc.persistence.RtcRecordingPersistence;
import zw.gov.mohcc.impilo.rtc.persistence.RtcSessionPersistence;
import zw.gov.mohcc.impilo.sessiontemplates.SessionMode;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplate;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplateRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recording lifecycle with template-driven policy gates. Recordings are gated
 * SEPARATELY from session provisioning: the session-template RecordingRule
 * decides who may start one and whether consent is a hard requirement —
 * fail-closed on every gate.
 */
@Service
public class RtcRecordingService {

    static final String EVT_RECORDING_REQUESTED = "impilo.rtc.recording.requested.v1";

    private static final Logger log = LoggerFactory.getLogger(RtcRecordingService.class);

    private final RtcGatewayProperties properties;
    private final RtcSessionPersistence sessions;
    private final RtcRecordingPersistence recordings;
    private final LiveKitEgressClient egressClient;
    private final RtcOutboxPublisher outboxPublisher;
    private final SessionTemplateRegistry templates;

    public RtcRecordingService(RtcGatewayProperties properties,
                               RtcSessionPersistence sessions,
                               RtcRecordingPersistence recordings,
                               LiveKitEgressClient egressClient,
                               RtcOutboxPublisher outboxPublisher,
                               SessionTemplateRegistry templates) {
        this.properties = properties;
        this.sessions = sessions;
        this.recordings = recordings;
        this.egressClient = egressClient;
        this.outboxPublisher = outboxPublisher;
        this.templates = templates;
    }

    public RtcRecordingRecord start(String sessionId, RtcRecordingStartRequest request) {
        if (!properties.getGateway().isEgressEnabled()) {
            throw new IllegalStateException("Recording egress is disabled on this gateway");
        }
        RtcSessionRecord session = sessions.findById(sessionId)
                .orElseThrow(() -> new RtcNotFoundException("RTC session not found"));
        if ("ENDED".equals(session.status())) {
            throw new IllegalArgumentException("Session has ended; recording cannot start");
        }

        SessionTemplate template = templateFor(session);
        SessionTemplate.RecordingRule rule = template.recording();
        if (rule == null) {
            // Fail-closed: no recording doctrine for this mode means no recording.
            throw new IllegalArgumentException(
                    "Recording is not permitted for session mode " + session.sessionMode());
        }
        if (!rule.canStart(request.startedByRole())) {
            throw new IllegalArgumentException("Role " + request.startedByRole()
                    + " may not start a recording for session mode " + template.sessionMode());
        }
        if (rule.consentRequired()
                && (session.consentReference() == null || session.consentReference().isBlank())) {
            throw new IllegalArgumentException(
                    "Recording requires a consent reference on the session and none was captured");
        }
        if (recordings.hasInFlight(sessionId)) {
            throw new IllegalArgumentException("A recording is already in progress for this session");
        }

        String layout = request.layout() == null || request.layout().isBlank()
                ? template.defaultLayout()
                : request.layout();
        LiveKitEgressClient.EgressStart start = egressClient.startRoomComposite(session.roomName(), layout);
        String bucket = properties.getRecording().getS3().getBucket();
        recordings.insertRequested(sessionId, start.egressId(), layout, bucket, start.filepath(),
                request.startedBy(), request.startedByRole(), session.consentReference());

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
        payload.put("consentReference", session.consentReference());
        payload.put("sensitivityClass", rule.sensitivityClass());
        payload.put("artifactOwner", rule.artifactOwner());
        outboxPublisher.append("RtcSession", session.id(), EVT_RECORDING_REQUESTED, payload);

        log.info("Recording requested for session {} (egress {}, role {})",
                sessionId, start.egressId(), request.startedByRole());
        return recordings.findByEgressId(start.egressId()).orElse(null);
    }

    public RtcRecordingRecord stop(String sessionId) {
        RtcRecordingRecord inFlight = recordings.findInFlight(sessionId)
                .orElseThrow(() -> new RtcNotFoundException("No recording in progress for this session"));
        egressClient.stopEgress(inFlight.egressId());
        recordings.markStopped(inFlight.egressId());
        log.info("Recording stop requested for session {} (egress {})", sessionId, inFlight.egressId());
        return recordings.findByEgressId(inFlight.egressId()).orElse(inFlight);
    }

    public List<RtcRecordingRecord> list(String sessionId) {
        sessions.findById(sessionId)
                .orElseThrow(() -> new RtcNotFoundException("RTC session not found"));
        return recordings.findBySessionId(sessionId);
    }

    /** Fail-closed: an unresolvable template refuses the recording outright. */
    private SessionTemplate templateFor(RtcSessionRecord session) {
        SessionMode mode = SessionMode.fromWire(session.sessionMode());
        if (mode == null) {
            mode = SessionMode.TELEMEDICINE;
        }
        try {
            return templates.get(mode);
        } catch (RuntimeException ex) {
            throw new IllegalStateException(
                    "Session template unavailable for mode " + mode + " — recording refused", ex);
        }
    }
}
