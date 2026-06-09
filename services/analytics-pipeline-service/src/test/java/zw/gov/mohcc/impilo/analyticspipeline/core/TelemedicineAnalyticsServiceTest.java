package zw.gov.mohcc.impilo.analyticspipeline.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.analyticspipeline.persistence.TelemedicineEventRepository;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class TelemedicineAnalyticsServiceTest {

    @Autowired
    private TelemedicineAnalyticsService service;

    @Autowired
    private TelemedicineEventRepository repository;

    @Test
    void ingestAndAggregate_persistsBeyondProcessMemory() {
        service.ingestEvent(Map.of("eventType", "SESSION_STARTED"));
        service.ingestEvent(Map.of("eventType", "SESSION_STARTED"));
        service.ingestEvent(Map.of("eventType", "SESSION_ENDED"));

        Map<String, Object> sla = service.slaAggregates(null, null, null);
        assertEquals(3L, sla.get("eventsAccepted"));
        @SuppressWarnings("unchecked")
        Map<String, Long> byType = (Map<String, Long>) sla.get("eventsByType");
        assertEquals(2L, byType.get("SESSION_STARTED"));
        assertEquals(1L, byType.get("SESSION_ENDED"));
        assertTrue(repository.count() >= 3);
    }
}
