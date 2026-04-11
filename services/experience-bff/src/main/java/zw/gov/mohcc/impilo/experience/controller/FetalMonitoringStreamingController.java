package zw.gov.mohcc.impilo.experience.controller;

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
import zw.gov.mohcc.impilo.experience.service.FetalMonitoringStreamService;

@RestController
@RequestMapping("/internal/v1/maternity/ctg")
public class FetalMonitoringStreamingController {

    private final FetalMonitoringController fetalMonitoringController;
    private final FetalMonitoringStreamService streamService;

    public FetalMonitoringStreamingController(
            FetalMonitoringController fetalMonitoringController,
            FetalMonitoringStreamService streamService
    ) {
        this.fetalMonitoringController = fetalMonitoringController;
        this.streamService = streamService;
    }

    @GetMapping(value = "/sessions/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamSession(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID sessionId
    ) {
        ResponseEntity<Map<String, Object>> sessionResponse = fetalMonitoringController.getSession(tenantId, sessionId);
        if (!sessionResponse.getStatusCode().is2xxSuccessful() || sessionResponse.getBody() == null) {
            return ResponseEntity.status(sessionResponse.getStatusCode()).build();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) sessionResponse.getBody().get("data");
        SseEmitter emitter = streamService.subscribe(tenantId, sessionId, snapshot);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }
}
