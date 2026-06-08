package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.AnalyticsPipelineServiceClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelemedicineAnalyticsControllerTest {

    @Test
    void slaAggregates_proxiesAnalyticsPipeline() throws Exception {
        AnalyticsPipelineServiceClient analyticsClient = mock(AnalyticsPipelineServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode payload = mapper.createObjectNode();
        payload.put("completedSessions", 12);
        payload.put("medianWaitSeconds", 45);
        when(analyticsClient.getTelemedicineSlaAggregates(eq("facility-1"), isNull(), isNull()))
                .thenReturn(payload);

        TelemedicineAnalyticsController controller = new TelemedicineAnalyticsController(analyticsClient);
        var response = controller.slaAggregates("facility-1", null, null, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody().get("data"));
        verify(analyticsClient).getTelemedicineSlaAggregates("facility-1", null, null);
    }

    @Test
    void ingestEvent_acceptsPayload() throws Exception {
        AnalyticsPipelineServiceClient analyticsClient = mock(AnalyticsPipelineServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode ack = mapper.createObjectNode();
        ack.put("eventId", "evt-1");
        when(analyticsClient.ingestTelemedicineEvent(any())).thenReturn(ack);

        TelemedicineAnalyticsController controller = new TelemedicineAnalyticsController(analyticsClient);
        var response = controller.ingestEvent(
                Map.of("eventType", "TELECONSULT_COMPLETED", "sessionId", "sess-1"),
                "req-2",
                "corr-2");

        assertEquals(202, response.getStatusCode().value());
        verify(analyticsClient).ingestTelemedicineEvent(any());
    }
}
