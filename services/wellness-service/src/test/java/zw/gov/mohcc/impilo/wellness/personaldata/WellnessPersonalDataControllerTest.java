package zw.gov.mohcc.impilo.wellness.personaldata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WellnessPersonalDataControllerTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WellnessPersonalDataController controller;

    @Test
    void listSourcesReturnsDataEnvelope() {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("FROM wellness_connected_sources"),
                org.mockito.ArgumentMatchers.eq("tenant-1"),
                org.mockito.ArgumentMatchers.eq("cpid-1")))
                .thenReturn(List.of(Map.of("source_name", "Health Connect")));
        ResponseEntity<Map<String, Object>> response = controller.listSources("tenant-1", "cpid-1");
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("data"));
    }

    @Test
    void connectSourceCreatesRow() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"activity\":true}");
        Map<String, Object> body = Map.of(
                "patientId", "cpid-1",
                "sourceType", "android_health_connect",
                "sourceName", "Google Health Connect");

        ResponseEntity<Map<String, Object>> response = controller.connectSource(
                "tenant-1", "actor-1", "CITIZEN", "WELLNESS", UUID.randomUUID().toString(), UUID.randomUUID().toString(), body);
        assertEquals(201, response.getStatusCode().value());
        verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void summaryBuildsPayload() {
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("FROM wellness_connected_sources"),
                org.mockito.ArgumentMatchers.eq("tenant-1"),
                org.mockito.ArgumentMatchers.eq("cpid-1")))
                .thenReturn(List.of());
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("FROM wellness_vitals_log"),
                org.mockito.ArgumentMatchers.eq("tenant-1"),
                org.mockito.ArgumentMatchers.eq("cpid-1")))
                .thenReturn(List.of());
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("FROM wellness_activities"),
                org.mockito.ArgumentMatchers.eq("tenant-1"),
                org.mockito.ArgumentMatchers.eq("cpid-1")))
                .thenReturn(List.of());
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("FROM wellness_remote_alerts"),
                org.mockito.ArgumentMatchers.eq("tenant-1"),
                org.mockito.ArgumentMatchers.eq("cpid-1")))
                .thenReturn(List.of());
        ResponseEntity<Map<String, Object>> response = controller.personalSummary("tenant-1", "cpid-1");
        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("cpid-1", data.get("patientId"));
    }
}
