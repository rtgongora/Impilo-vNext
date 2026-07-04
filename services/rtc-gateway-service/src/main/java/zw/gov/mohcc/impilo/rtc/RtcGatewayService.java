package zw.gov.mohcc.impilo.rtc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipant;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipantTokenRequest;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionProvisionRequest;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionRecord;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionResponse;
import zw.gov.mohcc.impilo.rtc.persistence.RtcSessionPersistence;
import zw.gov.mohcc.impilo.sessiontemplates.SessionMode;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplate;
import zw.gov.mohcc.impilo.sessiontemplates.SessionTemplateRegistry;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RtcGatewayService {

    private static final Logger log = LoggerFactory.getLogger(RtcGatewayService.class);

    /**
     * Wire values that legitimately reach sessionType today but are media kinds,
     * not session modes (khuluma + PCT send VIDEO/AUDIO). They resolve to the
     * TELEMEDICINE default quietly; anything else unknown logs a warn.
     */
    private static final Set<String> LEGACY_MEDIA_SESSION_TYPES = Set.of("VIDEO", "AUDIO");

    /**
     * Legacy caller roles mapped onto template roles per mode, kept so existing
     * callers (khuluma HOST calls, live-service event roles) do not break while
     * they migrate to the template role vocabulary. Every hit logs a deprecation warn.
     */
    private static final Map<SessionMode, Map<String, String>> LEGACY_ROLE_ALIASES = Map.of(
            SessionMode.TELEMEDICINE, Map.of(
                    "HOST", "PROVIDER"),
            SessionMode.LIVE_EVENT, Map.of(
                    "PRESENTER", "SPEAKER",
                    "ATTENDEE", "AUDIENCE",
                    "COHOST", "HOST",
                    "ADMIN", "HOST",
                    "SUPPORT", "MODERATOR",
                    "INTERPRETER", "SPEAKER"));

    private final RtcGatewayProperties properties;
    private final LiveKitTokenService tokenService;
    private final RtcSessionPersistence sessions;
    private final RtcOutboxPublisher outboxPublisher;
    private final MeterRegistry meterRegistry;
    private final SessionTemplateRegistry templates;
    private final RestTemplate restTemplate = new RestTemplate();

    public RtcGatewayService(RtcGatewayProperties properties,
                             LiveKitTokenService tokenService,
                             RtcSessionPersistence sessions,
                             RtcOutboxPublisher outboxPublisher,
                             MeterRegistry meterRegistry,
                             SessionTemplateRegistry templates) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.sessions = sessions;
        this.outboxPublisher = outboxPublisher;
        this.meterRegistry = meterRegistry;
        this.templates = templates;
    }

    public RtcSessionResponse provision(RtcSessionProvisionRequest request) {
        try {
            validateProvisioningRequest(request);

            // Provision is idempotent per sessionId: concurrent first-media-token
            // requests from different participants must both succeed — the loser
            // of the insert race joins the winner's room.
            RtcSessionRecord existing = sessions.findById(request.sessionId()).orElse(null);
            if (existing != null) {
                return joinExisting(existing, request);
            }

            SessionMode mode = resolveMode(request.sessionType());
            SessionTemplate template = templateFor(mode);
            String roomName = roomName(request, template);
            if (!properties.getGateway().isDevModeEnabled()) {
                tokenService.assertLiveKitConfigured();
                createLiveKitRoom(roomName, template);
            }

            LiveKitTokenService.TokenResult token = issueTemplateToken(roomName, mode, template, request.participant());
            RtcSessionRecord record = new RtcSessionRecord(
                    request.sessionId(),
                    request.tenantId(),
                    provider(),
                    roomName,
                    roomUrl(roomName),
                    "PROVISIONED",
                    mode.name(),
                    owningService(request, template),
                    request.owningRef(),
                    request.patientId(),
                    request.providerId(),
                    request.encounterId(),
                    request.referralId(),
                    "rtc:" + roomName,
                    capabilities(),
                    mediaPolicy(template),
                    Instant.now(),
                    Instant.now()
            );
            try {
                sessions.save(record);
            } catch (org.springframework.dao.DataIntegrityViolationException raced) {
                RtcSessionRecord winner = sessions.findById(request.sessionId())
                        .orElseThrow(() -> raced);
                return joinExisting(winner, request);
            }
            outboxPublisher.append("RtcSession", record.id(), "RTC_SESSION_PROVISIONED", sessionPayload(record));
            meterRegistry.counter("impilo_rtc_session_provisioned_total", "provider", provider()).increment();
            return toResponse(record, token);
        } catch (RuntimeException ex) {
            meterRegistry.counter("impilo_rtc_session_provision_failed_total",
                    "provider", provider(),
                    "reason", classifyFailure(ex)).increment();
            throw ex;
        }
    }

    public RtcSessionResponse get(String sessionId) {
        RtcSessionRecord record = sessions.findById(sessionId)
                .orElseThrow(() -> new RtcNotFoundException("RTC session not found"));
        return toResponse(record, null);
    }

    /** Idempotent re-provision: issue this participant a token for the already-provisioned room. */
    private RtcSessionResponse joinExisting(RtcSessionRecord existing, RtcSessionProvisionRequest request) {
        if (request.tenantId() != null && !request.tenantId().equals(existing.tenantId())) {
            throw new IllegalArgumentException("Session belongs to a different tenant");
        }
        if ("ENDED".equals(existing.status())) {
            throw new IllegalArgumentException("Session has ended; provision a new session");
        }
        SessionMode mode = modeOf(existing);
        LiveKitTokenService.TokenResult token =
                issueTemplateToken(existing.roomName(), mode, templateFor(mode), request.participant());
        meterRegistry.counter("impilo_rtc_token_issued_total", "provider", existing.provider()).increment();
        return toResponse(existing, token);
    }

    public RtcSessionResponse issueToken(String sessionId, RtcParticipantTokenRequest request) {
        RtcSessionRecord record = sessions.findById(sessionId)
                .orElseThrow(() -> new RtcNotFoundException("RTC session not found"));
        SessionMode mode = modeOf(record);
        LiveKitTokenService.TokenResult token =
                issueTemplateToken(record.roomName(), mode, templateFor(mode), request.participant());
        meterRegistry.counter("impilo_rtc_token_issued_total", "provider", record.provider()).increment();
        return toResponse(record, token);
    }

    public Map<String, Object> opsHealth() {
        boolean devModeEnabled = properties.getGateway().isDevModeEnabled();
        boolean livekitEnabled = properties.getLivekit().isEnabled();
        boolean livekitConfigured = livekitConfigured();
        boolean productionReady = !devModeEnabled && livekitEnabled && livekitConfigured;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("provider", provider());
        out.put("devModeEnabled", devModeEnabled);
        out.put("livekitEnabled", livekitEnabled);
        out.put("livekitConfigured", livekitConfigured);
        out.put("productionReady", productionReady);
        out.put("serverUrl", serverUrl());
        out.put("activeSessions", sessions.countAll());
        return out;
    }

    public RtcSessionResponse end(String sessionId) {
        RtcSessionRecord record = sessions.findById(sessionId)
                .orElseThrow(() -> new RtcNotFoundException("RTC session not found"));
        if (!properties.getGateway().isDevModeEnabled() && properties.getLivekit().isEnabled()) {
            deleteLiveKitRoom(record.roomName());
        }
        RtcSessionRecord ended = sessions.save(record.withStatus("ENDED"));
        outboxPublisher.append("RtcSession", ended.id(), "RTC_SESSION_ENDED", sessionPayload(ended));
        meterRegistry.counter("impilo_rtc_session_ended_total", "provider", ended.provider()).increment();
        return toResponse(ended, null);
    }

    // ── Session-template resolution ─────────────────────────────────

    /** Wire sessionType → SessionMode; unknown/blank defaults to TELEMEDICINE (backward compat). */
    private SessionMode resolveMode(String sessionType) {
        SessionMode mode = SessionMode.fromWire(sessionType);
        if (mode != null) {
            return mode;
        }
        if (sessionType != null && !sessionType.isBlank()) {
            String normalized = sessionType.trim().toUpperCase(Locale.ROOT);
            if (LEGACY_MEDIA_SESSION_TYPES.contains(normalized)) {
                log.debug("Legacy media sessionType '{}' — defaulting session mode to TELEMEDICINE", sessionType);
            } else {
                log.warn("Unknown sessionType '{}' — defaulting session mode to TELEMEDICINE", sessionType);
            }
        }
        return SessionMode.TELEMEDICINE;
    }

    private SessionMode modeOf(RtcSessionRecord record) {
        SessionMode mode = SessionMode.fromWire(record.sessionMode());
        return mode != null ? mode : SessionMode.TELEMEDICINE;
    }

    /** Registry is fail-closed so this cannot return null, but stay defensive. */
    private SessionTemplate templateFor(SessionMode mode) {
        try {
            return templates.get(mode);
        } catch (RuntimeException ex) {
            log.error("Session template lookup failed for mode {} — falling back to property defaults", mode, ex);
            return null;
        }
    }

    private LiveKitTokenService.TokenResult issueTemplateToken(String roomName, SessionMode mode,
                                                               SessionTemplate template, RtcParticipant participant) {
        if (template == null) {
            return tokenService.issueParticipantToken(roomName, participant);
        }
        SessionTemplate.TokenGrantProfile grant = resolveGrant(mode, template, participant.role());
        return tokenService.issueParticipantToken(roomName, participant, grant, template.tokenTtlSeconds());
    }

    /**
     * Template grant for the participant role. Frontends cannot bypass token policy:
     * a role without a grant profile in the mode's template is refused outright.
     */
    private SessionTemplate.TokenGrantProfile resolveGrant(SessionMode mode, SessionTemplate template, String role) {
        SessionTemplate.TokenGrantProfile grant = template.grantFor(role);
        if (grant != null) {
            return grant;
        }
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        String alias = LEGACY_ROLE_ALIASES.getOrDefault(mode, Map.of()).get(normalized);
        if (alias != null) {
            SessionTemplate.TokenGrantProfile aliased = template.grantFor(alias);
            if (aliased != null) {
                log.warn("Deprecated role '{}' for session mode {} — mapped to template role '{}'. "
                        + "Callers should migrate to template roles.", role, mode, alias);
                return aliased;
            }
        }
        throw new IllegalArgumentException("Role " + role + " not permitted for session mode " + mode);
    }

    private String owningService(RtcSessionProvisionRequest request, SessionTemplate template) {
        if (request.owningService() != null && !request.owningService().isBlank()) {
            return request.owningService();
        }
        return template != null ? template.owningService() : null;
    }

    private RtcSessionResponse toResponse(RtcSessionRecord record, LiveKitTokenService.TokenResult token) {
        return new RtcSessionResponse(
                record.id(),
                record.provider(),
                record.roomName(),
                record.roomUrl(),
                token == null ? null : token.accessToken(),
                token == null ? null : token.expiresAt(),
                record.status(),
                record.channel(),
                record.capabilities(),
                record.mediaPolicy()
        );
    }

    private void createLiveKitRoom(String roomName, SessionTemplate template) {
        restTemplate.postForEntity(
                liveKitUrl(properties.getLivekit().getCreateRoomPath()),
                new HttpEntity<>(createRoomBody(roomName, template), liveKitHeaders(roomName)),
                Map.class
        );
    }

    /** Twirp CreateRoom body; max participants comes from the mode template when it caps (>0). */
    Map<String, Object> createRoomBody(String roomName, SessionTemplate template) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", roomName);
        body.put("emptyTimeout", properties.getLivekit().getDefaultEmptyTimeoutSeconds());
        int maxParticipants = template != null && template.maxParticipants() > 0
                ? template.maxParticipants()
                : properties.getLivekit().getMaxParticipants();
        body.put("maxParticipants", maxParticipants);
        return body;
    }

    private void deleteLiveKitRoom(String roomName) {
        Map<String, Object> body = Map.of("room", roomName);
        restTemplate.postForEntity(
                liveKitUrl(properties.getLivekit().getDeleteRoomPath()),
                new HttpEntity<>(body, liveKitHeaders(roomName)),
                Map.class
        );
    }

    private HttpHeaders liveKitHeaders(String roomName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokenService.issueRoomAdminToken(roomName));
        return headers;
    }

    private String liveKitUrl(String path) {
        String base = properties.getLivekit().getUrl();
        String suffix = path == null || path.isBlank() ? "" : path;
        if (base.endsWith("/") && suffix.startsWith("/")) {
            return base.substring(0, base.length() - 1) + suffix;
        }
        if (!base.endsWith("/") && !suffix.startsWith("/")) {
            return base + "/" + suffix;
        }
        return base + suffix;
    }

    private String roomUrl(String roomName) {
        String url = properties.getLivekit().getClientUrl();
        if (url == null || url.isBlank()) {
            url = properties.getLivekit().getUrl();
        }
        if (url == null || url.isBlank()) {
            return "dev://livekit";
        }
        if (url.startsWith("https://")) {
            return "wss://" + url.substring("https://".length());
        }
        if (url.startsWith("http://")) {
            return "ws://" + url.substring("http://".length());
        }
        return url;
    }

    private String roomName(RtcSessionProvisionRequest request, SessionTemplate template) {
        String raw = request.sessionId() == null || request.sessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.sessionId();
        String normalized = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
        String prefix = template != null
                ? template.effectiveRoomPrefix()
                : properties.getGateway().getRoomPrefix();
        return prefix + "-" + normalized;
    }

    private String provider() {
        return properties.getGateway().getProvider() == null ? "LIVEKIT" : properties.getGateway().getProvider();
    }

    private boolean livekitConfigured() {
        if (!properties.getLivekit().isEnabled()) {
            return false;
        }
        String url = properties.getLivekit().getUrl();
        String apiKey = properties.getLivekit().getApiKey();
        String apiSecret = properties.getLivekit().getApiSecret();
        return url != null && !url.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank();
    }

    private String serverUrl() {
        String url = properties.getLivekit().getClientUrl();
        if (url == null || url.isBlank()) {
            url = properties.getLivekit().getUrl();
        }
        if (url == null || url.isBlank()) {
            return devModeEnabled() ? "dev://livekit" : "";
        }
        if (url.startsWith("https://")) {
            return "wss://" + url.substring("https://".length());
        }
        if (url.startsWith("http://")) {
            return "ws://" + url.substring("http://".length());
        }
        return url;
    }

    private boolean devModeEnabled() {
        return properties.getGateway().isDevModeEnabled();
    }

    private Map<String, Boolean> capabilities() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        out.put("audio", true);
        out.put("video", true);
        out.put("data", true);
        out.put("recording", properties.getGateway().isRecordingEnabled());
        return out;
    }

    private Map<String, Object> mediaPolicy(SessionTemplate template) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recordingAllowed", properties.getGateway().isRecordingEnabled());
        out.put("egressAllowed", properties.getGateway().isEgressEnabled());
        out.put("tokenTtlSeconds", template != null
                ? template.tokenTtlSeconds()
                : properties.getGateway().getTokenTtlSeconds());
        out.put("failClosed", properties.getGateway().isFailClosed());
        out.put("clinicalWorkflowOwner", template != null ? template.owningService() : "PCT");
        return out;
    }

    private void validateProvisioningRequest(RtcSessionProvisionRequest request) {
        String sessionType = normalizeSessionType(request.sessionType());
        String purposeOfUse = normalizePurposeOfUse(request.purposeOfUse());
        if (!properties.getGateway().isRequireConsentReferenceForMedia()) {
            return;
        }
        if (!"VIDEO".equals(sessionType) && !"AUDIO".equals(sessionType)) {
            return;
        }
        if (properties.getGateway().isAllowEmergencyWithoutConsent() && "EMERGENCY".equals(purposeOfUse)) {
            return;
        }
        if (request.consentReference() == null || request.consentReference().isBlank()) {
            throw new IllegalArgumentException("consentReference is required for governed RTC media sessions");
        }
    }

    private String normalizeSessionType(String value) {
        if (value == null || value.isBlank()) {
            return "VIDEO";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizePurposeOfUse(String value) {
        if (value == null || value.isBlank()) {
            return "TREATMENT";
        }
        return value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> sessionPayload(RtcSessionRecord record) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", record.tenantId());
        payload.put("status", record.status());
        payload.put("roomName", record.roomName());
        payload.put("sessionMode", record.sessionMode());
        payload.put("owningService", record.owningService());
        payload.put("owningRef", record.owningRef());
        payload.put("patientId", record.patientId());
        payload.put("providerId", record.providerId());
        payload.put("encounterId", record.encounterId());
        payload.put("referralId", record.referralId());
        return payload;
    }

    private String classifyFailure(Throwable ex) {
        String message = ex == null || ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        if (ex instanceof IllegalArgumentException) {
            return "invalid_request";
        }
        if (message.contains("livekit")) {
            return "provider_unavailable";
        }
        if (message.contains("token")) {
            return "token_issue";
        }
        return "unknown";
    }
}
