package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrustProviderControllerTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void requestBreakGlass_requiresReason() {
        TrustProviderController controller = new TrustProviderController(mock(TshepoAuthzServiceClient.class));
        var response = controller.requestBreakGlass(
                TENANT.toString(),
                "provider-1",
                "req-1",
                "corr-1",
                Map.of("resourceType", "PATIENT_RECORD"));
        assertEquals(400, response.getStatusCode().value());
        assertEquals("REASON_REQUIRED", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void requestBreakGlass_delegatesToTshepoAndShapesResponse() throws Exception {
        TshepoAuthzServiceClient authzClient = mock(TshepoAuthzServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode upstream = mapper.createObjectNode();
        upstream.put("id", "bg-99");
        upstream.put("actorId", "provider-1");
        upstream.put("reason", "Cardiac arrest");
        upstream.put("reviewStatus", "PENDING_REVIEW");
        when(authzClient.requestBreakGlass(any())).thenReturn(upstream);

        TrustProviderController controller = new TrustProviderController(authzClient);
        var response = controller.requestBreakGlass(
                TENANT.toString(),
                "provider-1",
                "req-2",
                "corr-2",
                Map.of(
                        "reason", "Cardiac arrest",
                        "resourceType", "PATIENT_RECORD",
                        "patientId", "cpid-77"));

        assertEquals(201, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("bg-99", data.get("id"));
        assertEquals("break_glass_request", data.get("type"));
        verify(authzClient).requestBreakGlass(any());
    }
}
