package zw.gov.mohcc.impilo.experience.service;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryFetalMonitoringStreamServiceTest {

    @Test
    void subscribeRegistersAndCompletesEmitter() {
        InMemoryFetalMonitoringStreamService service = new InMemoryFetalMonitoringStreamService();
        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        SseEmitter emitter = service.subscribe("tenant-moh-zw", sessionId, Map.of("id", sessionId.toString()));

        assertEquals(1, service.subscriberCount("tenant-moh-zw", sessionId));
        service.publishChunkRecorded("tenant-moh-zw", sessionId, Map.of("channel", "FHR"));

        emitter.complete();
    }
}
