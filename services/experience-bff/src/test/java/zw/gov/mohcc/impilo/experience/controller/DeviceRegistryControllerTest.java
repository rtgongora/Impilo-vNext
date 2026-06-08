package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.IotIngestionServiceClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class DeviceRegistryControllerTest {

    private IotIngestionServiceClient iotClient;
    private DeviceRegistryController controller;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        iotClient = Mockito.mock(IotIngestionServiceClient.class);
        controller = new DeviceRegistryController(iotClient, objectMapper);
    }

    @Test
    void listDevices_returnsRegistryEnvelope() {
        ArrayNode devices = objectMapper.createArrayNode();
        ObjectNode row = objectMapper.createObjectNode();
        row.put("deviceId", "11111111-1111-1111-1111-111111111111");
        row.put("status", "ACTIVE");
        devices.add(row);
        Mockito.when(iotClient.listDevices("actor-1")).thenReturn(devices);

        ResponseEntity<Map<String, Object>> response =
                controller.listDevices("tenant-1", "req-1", "corr-1", "actor-1");

        assertEquals(200, response.getStatusCode().value());
        List<?> data = (List<?>) response.getBody().get("data");
        assertFalse(data.isEmpty());
        Map<?, ?> meta = (Map<?, ?>) response.getBody().get("meta");
        assertEquals("req-1", meta.get("request_id"));
    }

    @Test
    void registerDevice_returnsPendingVerification() {
        ObjectNode device = objectMapper.createObjectNode();
        device.put("status", "PENDING_VERIFICATION");
        device.put("trustLevel", "BASIC");
        Mockito.when(iotClient.registerDevice(any())).thenReturn(device);

        ResponseEntity<Map<String, Object>> response = controller.registerDevice(
                "tenant-1", "req-2", "corr-2",
                Map.of("externalDeviceId", "fp-123", "deviceType", "MOBILE", "os", "Android 15"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("PENDING_VERIFICATION", data.get("status"));
        assertEquals("BASIC", data.get("trustLevel"));
    }

    @Test
    void revokeDevice_marksRevoked() {
        ObjectNode revoked = objectMapper.createObjectNode();
        revoked.put("status", "REVOKED");
        Mockito.when(iotClient.revokeDevice(eq("dev-a1b2c3d4"), any())).thenReturn(revoked);

        ResponseEntity<Map<String, Object>> response =
                controller.revokeDevice("dev-a1b2c3d4", "tenant-1", "req-3", "corr-3", Map.of("reason", "LOST"));

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("REVOKED", data.get("status"));
    }
}
