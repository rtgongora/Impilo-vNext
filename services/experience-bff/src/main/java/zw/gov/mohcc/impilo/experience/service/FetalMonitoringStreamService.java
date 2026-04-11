package zw.gov.mohcc.impilo.experience.service;

import java.util.Map;
import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface FetalMonitoringStreamService {

    SseEmitter subscribe(String tenantId, UUID sessionId, Map<String, Object> snapshot);

    void publishSessionOpened(String tenantId, UUID sessionId, Map<String, Object> sessionPayload);

    void publishChunkRecorded(String tenantId, UUID sessionId, Map<String, Object> chunkPayload);

    void publishAnnotationRecorded(String tenantId, UUID sessionId, Map<String, Object> annotationPayload);
}
