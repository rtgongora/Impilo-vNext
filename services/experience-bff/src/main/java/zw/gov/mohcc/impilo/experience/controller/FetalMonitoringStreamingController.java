package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.service.FetalMonitoringStreamService;

@RestController
@RequestMapping("/internal/v1/maternity/ctg")
public class FetalMonitoringStreamingController {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final FetalMonitoringController fetalMonitoringController;
    private final FetalMonitoringStreamService streamService;

    public FetalMonitoringStreamingController(
            ObjectMapper objectMapper,
            FetalMonitoringController fetalMonitoringController,
            FetalMonitoringStreamService streamService
    ) {
        this.objectMapper = objectMapper;
        this.fetalMonitoringController = fetalMonitoringController;
        this.streamService = streamService;
    }

    @GetMapping(value = "/sessions/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamSession(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable UUID sessionId
    ) {
        ResponseEntity<Map<String, Object>> sessionResponse =
                fetalMonitoringController.getSession(requestId, correlationId, sessionId.toString());
        if (!sessionResponse.getStatusCode().is2xxSuccessful() || sessionResponse.getBody() == null) {
            return ResponseEntity.status(sessionResponse.getStatusCode()).build();
        }

        Object data = sessionResponse.getBody().get("data");
        Map<String, Object> snapshot = Map.of();
        if (data instanceof JsonNode jsonNode) {
            snapshot = objectMapper.convertValue(jsonNode, MAP_TYPE);
        } else if (data instanceof Map<?, ?> map) {
            //noinspection unchecked
            snapshot = (Map<String, Object>) map;
        }
        SseEmitter emitter = streamService.subscribe(tenantId, sessionId, snapshot);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }
}
