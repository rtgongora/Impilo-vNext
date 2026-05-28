package zw.gov.mohcc.impilo.rtc;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import zw.gov.mohcc.impilo.rtc.model.RtcParticipantTokenRequest;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionProvisionRequest;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionRecord;
import zw.gov.mohcc.impilo.rtc.model.RtcSessionResponse;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class RtcGatewayService {
    private final RtcGatewayProperties properties;
    private final LiveKitTokenService tokenService;
    private final RtcSessionStore store;
    private final MeterRegistry meterRegistry;
    private final RestTemplate restTemplate = new RestTemplate();

    public RtcGatewayService(RtcGatewayProperties properties,
                             LiveKitTokenService tokenService,
                             RtcSessionStore store,
                             MeterRegistry meterRegistry) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.store = store;
        this.meterRegistry = meterRegistry;
    }

    public RtcSessionResponse provision(RtcSessionProvisionRequest request) {
        try {
            validateProvisioningRequest(request);
            String roomName = roomName(request);
            if (!properties.getGateway().isDevModeEnabled()) {
                tokenService.assertLiveKitConfigured();
                createLiveKitRoom(roomName);
            }

            LiveKitTokenService.TokenResult token = tokenService.issueParticipantToken(roomName, request.participant());
            RtcSessionRecord record = new RtcSessionRecord(
                    request.sessionId(),
                    request.tenantId(),
                    provider(),
                    roomName,
                    roomUrl(roomName),
                    "PROVISIONED",
                    request.patientId(),
                    request.providerId(),
                    request.encounterId(),
                    request.referralId(),
                    "rtc:" + roomName,
                    capabilities(),
                    mediaPolicy(),
                    Instant.now(),
                    Instant.now()
            );
            store.save(record);
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
        RtcSessionRecord record = store.get(sessionId)
                .orElseThrow(() -> new RtcNotFoundException("RTC session not found"));
        return toResponse(record, null);
    }

    public RtcSessionResponse issueToken(String sessionId, RtcParticipantTokenRequest request) {
        RtcSessionRecord record = store.get(sessionId)
                .orElseThrow(() -> new RtcNotFoundException("RTC session not found"));
        LiveKitTokenService.TokenResult token = tokenService.issueParticipantToken(record.roomName(), request.participant());
        meterRegistry.counter("impilo_rtc_token_issued_total", "provider", record.provider()).increment();
        return toResponse(record, token);
    }

    public RtcSessionResponse end(String sessionId) {
        RtcSessionRecord record = store.get(sessionId)
                .orElseThrow(() -> new RtcNotFoundException("RTC session not found"));
        if (!properties.getGateway().isDevModeEnabled() && properties.getLivekit().isEnabled()) {
            deleteLiveKitRoom(record.roomName());
        }
        RtcSessionRecord ended = store.save(record.withStatus("ENDED"));
        meterRegistry.counter("impilo_rtc_session_ended_total", "provider", ended.provider()).increment();
        return toResponse(ended, null);
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

    private void createLiveKitRoom(String roomName) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", roomName);
        body.put("emptyTimeout", properties.getLivekit().getDefaultEmptyTimeoutSeconds());
        body.put("maxParticipants", properties.getLivekit().getMaxParticipants());
        restTemplate.postForEntity(
                liveKitUrl(properties.getLivekit().getCreateRoomPath()),
                new HttpEntity<>(body, liveKitHeaders(roomName)),
                Map.class
        );
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

    private String roomName(RtcSessionProvisionRequest request) {
        String raw = request.sessionId() == null || request.sessionId().isBlank()
                ? UUID.randomUUID().toString()
                : request.sessionId();
        String normalized = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
        return properties.getGateway().getRoomPrefix() + "-" + normalized;
    }

    private String provider() {
        return properties.getGateway().getProvider() == null ? "LIVEKIT" : properties.getGateway().getProvider();
    }

    private Map<String, Boolean> capabilities() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        out.put("audio", true);
        out.put("video", true);
        out.put("data", true);
        out.put("recording", properties.getGateway().isRecordingEnabled());
        return out;
    }

    private Map<String, Object> mediaPolicy() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recordingAllowed", properties.getGateway().isRecordingEnabled());
        out.put("egressAllowed", properties.getGateway().isEgressEnabled());
        out.put("tokenTtlSeconds", properties.getGateway().getTokenTtlSeconds());
        out.put("failClosed", properties.getGateway().isFailClosed());
        out.put("clinicalWorkflowOwner", "PCT");
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
