package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.AssetRegistryServiceClient;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;
import zw.gov.mohcc.impilo.experience.client.KhulumaServiceClient;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Unit tests for the equipment-operations BFF composition. Mocks the sovereign clients
 * (no datasource / no Redis needed — controller is stateless) and asserts proxy wiring +
 * the Khuluma-notify and Nompilo-guidance composition behaviour.
 */
class EquipmentOperationsBffControllerTest {

    private AssetRegistryServiceClient asset;
    private KhulumaServiceClient khuluma;
    private GuidanceServiceClient guidance;
    private EquipmentOperationsBffController controller;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        asset = Mockito.mock(AssetRegistryServiceClient.class);
        khuluma = Mockito.mock(KhulumaServiceClient.class);
        guidance = Mockito.mock(GuidanceServiceClient.class);
        controller = new EquipmentOperationsBffController(asset, khuluma, guidance);
    }

    @Test
    void list_proxiesToAssetClient() {
        ObjectNode payload = mapper.createObjectNode();
        payload.putArray("items");
        Mockito.when(asset.listEquipment("FAC-1", 0, 20)).thenReturn(payload);

        ResponseEntity<?> r = controller.list("FAC-1", 0, 20);

        assertEquals(200, r.getStatusCode().value());
        Mockito.verify(asset).listEquipment("FAC-1", 0, 20);
    }

    @Test
    void register_proxiesBody() {
        ObjectNode body = mapper.createObjectNode();
        body.put("name", "Ventilator A");
        ObjectNode resp = mapper.createObjectNode();
        resp.put("equipment_id", UUID.randomUUID().toString());
        Mockito.when(asset.registerEquipment(any())).thenReturn(resp);

        ResponseEntity<?> r = controller.register(body);

        assertEquals(200, r.getStatusCode().value());
        Mockito.verify(asset).registerEquipment(body);
    }

    @Test
    void readiness_proxiesFacilityRef() {
        ObjectNode resp = mapper.createObjectNode();
        resp.put("total_gaps", 2);
        Mockito.when(asset.readiness("FAC-7")).thenReturn(resp);

        ResponseEntity<?> r = controller.readiness("FAC-7");

        assertEquals(200, r.getStatusCode().value());
        assertEquals(2, ((com.fasterxml.jackson.databind.JsonNode) r.getBody()).get("total_gaps").asInt());
    }

    @Test
    void readinessGuidance_callsGuidanceWithAssetsDomain() {
        Mockito.when(guidance.getEducation("assets", 0, 5)).thenReturn(mapper.createObjectNode());

        controller.readinessGuidance(0, 5);

        Mockito.verify(guidance).getEducation("assets", 0, 5);
    }

    @Test
    void notifyAlert_requestsKhulumaAndMarksAlert() {
        UUID alertId = UUID.randomUUID();
        ObjectNode body = mapper.createObjectNode();
        body.put("message", "Fridge offline");
        Mockito.when(asset.markAlertKhuluma(alertId)).thenReturn(mapper.createObjectNode());

        ResponseEntity<Map<String, Object>> r = controller.notifyAlert(alertId, body, "tenant-1");

        assertEquals(200, r.getStatusCode().value());
        assertTrue((Boolean) r.getBody().get("khuluma_requested"));

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(khuluma).dispatch(eq("tenant-1"), captor.capture());
        assertEquals("ASSET_IOT_ALERT", captor.getValue().get("category"));
        assertEquals(alertId.toString(), captor.getValue().get("alert_id"));
        Mockito.verify(asset).markAlertKhuluma(alertId);
    }

    @Test
    void reportFault_proxies() {
        UUID eq = UUID.randomUUID();
        ObjectNode body = mapper.createObjectNode();
        body.put("summary", "Leaking");
        Mockito.when(asset.reportFault(eq(eq), any())).thenReturn(mapper.createObjectNode());

        controller.reportFault(eq, body);

        Mockito.verify(asset).reportFault(eq(eq), any());
    }
}
