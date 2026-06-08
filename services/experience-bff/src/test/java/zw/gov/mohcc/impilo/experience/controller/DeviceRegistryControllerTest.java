package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DeviceRegistryControllerTest {

    @Test
    void listDevices_returnsRegistryEnvelope() {
        DeviceRegistryController controller = new DeviceRegistryController();
        ResponseEntity<Map<String, Object>> response =
                controller.listDevices("tenant-1", "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        List<?> devices = (List<?>) response.getBody().get("data");
        assertFalse(devices.isEmpty());
        Map<?, ?> meta = (Map<?, ?>) response.getBody().get("meta");
        assertEquals("req-1", meta.get("request_id"));
    }

    @Test
    void registerDevice_returnsPendingVerification() {
        DeviceRegistryController controller = new DeviceRegistryController();
        ResponseEntity<Map<String, Object>> response = controller.registerDevice(
                "tenant-1", "req-2", "corr-2",
                Map.of("deviceName", "Test Phone", "deviceType", "MOBILE", "os", "Android 15"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("PENDING_VERIFICATION", data.get("status"));
        assertEquals("LOW", data.get("trustLevel"));
    }

    @Test
    void revokeDevice_marksRevoked() {
        DeviceRegistryController controller = new DeviceRegistryController();
        ResponseEntity<Map<String, Object>> response =
                controller.revokeDevice("dev-a1b2c3d4", "tenant-1", "req-3", "corr-3");

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("REVOKED", data.get("status"));
    }
}
