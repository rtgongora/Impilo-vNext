package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationStatusMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void mapsHealthyLabAdapterToConnectedLabDomain() throws Exception {
        JsonNode body = MAPPER.readTree("""
                [
                  {
                    "id": "rt-1",
                    "domain": "LIMS",
                    "enabled": true,
                    "lastHealth": "HEALTHY",
                    "displayName": "Trinity LIMS"
                  }
                ]
                """);

        List<Map<String, Object>> citizen = IntegrationStatusMapper.mapForCitizen(body);
        List<Map<String, Object>> provider = IntegrationStatusMapper.mapForProvider(body);

        assertEquals(1, citizen.size());
        assertEquals("LAB", citizen.get(0).get("domain"));
        assertEquals("CONNECTED", citizen.get(0).get("status"));
        assertEquals("Trinity LIMS", citizen.get(0).get("label"));
        // Citizen view must never include vendor / correlation
        assertFalse(citizen.get(0).containsKey("vendor"));
        assertFalse(citizen.get(0).containsKey("correlation_id"));

        // Provider view exposes the same fields plus the privileged ones
        assertEquals("CONNECTED", provider.get(0).get("status"));
        assertTrue(provider.get(0).containsKey("vendor"));
        assertTrue(provider.get(0).containsKey("correlation_id"));
    }

    @Test
    void disabledRouteBecomesNotConfigured() throws Exception {
        JsonNode body = MAPPER.readTree("""
                [
                  { "id": "rt-2", "domain": "PACS", "enabled": false }
                ]
                """);

        List<Map<String, Object>> rows = IntegrationStatusMapper.mapForCitizen(body);
        assertEquals(1, rows.size());
        assertEquals("IMAGING", rows.get(0).get("domain"));
        assertEquals("NOT_CONFIGURED", rows.get(0).get("status"));
    }

    @Test
    void unhealthyVendorBecomesTemporarilyUnavailable() throws Exception {
        JsonNode body = MAPPER.readTree("""
                [
                  {
                    "id": "rt-3",
                    "type": "MUSHEX-GATEWAY",
                    "enabled": true,
                    "lastHealth": "DOWN",
                    "displayName": "MusheX Payments"
                  }
                ]
                """);

        List<Map<String, Object>> rows = IntegrationStatusMapper.mapForCitizen(body);
        assertEquals("PAYMENT", rows.get(0).get("domain"));
        assertEquals("TEMPORARILY_UNAVAILABLE", rows.get(0).get("status"));
    }

    @Test
    void explicitStatusOverridesInferredHealth() throws Exception {
        JsonNode body = MAPPER.readTree("""
                [
                  {
                    "id": "rt-4",
                    "domain": "TELEMEDICINE",
                    "enabled": true,
                    "lastHealth": "HEALTHY",
                    "status": "ACTION_REQUIRED"
                  }
                ]
                """);

        List<Map<String, Object>> rows = IntegrationStatusMapper.mapForCitizen(body);
        assertEquals("ACTION_REQUIRED", rows.get(0).get("status"));
        assertEquals("TELEHEALTH", rows.get(0).get("domain"));
    }

    @Test
    void wrappedDataEnvelopeIsUnwrapped() throws Exception {
        JsonNode body = MAPPER.readTree("""
                {
                  "data": [
                    { "id": "rt-5", "domain": "NHUME", "enabled": true, "lastHealth": "HEALTHY" }
                  ]
                }
                """);

        List<Map<String, Object>> rows = IntegrationStatusMapper.mapForCitizen(body);
        assertEquals(1, rows.size());
        assertEquals("DELIVERY", rows.get(0).get("domain"));
    }

    @Test
    void nullBodyReturnsEmptyList() {
        List<Map<String, Object>> rows = IntegrationStatusMapper.mapForCitizen(null);
        assertTrue(rows.isEmpty());
    }

    @Test
    void singleObjectMappingForCitizen() throws Exception {
        JsonNode node = MAPPER.readTree("""
                { "id": "rt-6", "domain": "COMMS", "enabled": true, "lastHealth": "HEALTHY" }
                """);

        Map<String, Object> row = IntegrationStatusMapper.mapOneForCitizen(node);
        assertEquals("COMMS", row.get("domain"));
        assertEquals("CONNECTED", row.get("status"));
        assertNull(row.get("vendor"));
    }
}
