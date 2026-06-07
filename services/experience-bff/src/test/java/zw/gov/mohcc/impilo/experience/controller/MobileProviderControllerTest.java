package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MobileProviderControllerTest {

    private static final String TENANT = "00000000-0000-0000-0000-000000000001";

    @Test
    void activateBreakGlass_requiresReason() {
        MobileProviderController controller = new MobileProviderController(
                mock(PctServiceClient.class),
                mock(VarapiServiceClient.class),
                mock(TshepoAuthzServiceClient.class));
        var response = controller.activateBreakGlass(
                Map.of("resourceType", "PATIENT_RECORD"),
                TENANT,
                "provider-1");
        assertEquals(400, response.getStatusCode().value());
        assertEquals("REASON_REQUIRED", ((Map<?, ?>) response.getBody().get("error")).get("code"));
    }

    @Test
    void activateBreakGlass_delegatesToTshepo() throws Exception {
        TshepoAuthzServiceClient authzClient = mock(TshepoAuthzServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode upstream = mapper.createObjectNode();
        upstream.put("id", "bg-mobile-1");
        upstream.put("reviewStatus", "PENDING_REVIEW");
        when(authzClient.requestBreakGlass(any())).thenReturn(upstream);

        MobileProviderController controller = new MobileProviderController(
                mock(PctServiceClient.class),
                mock(VarapiServiceClient.class),
                authzClient);

        var response = controller.activateBreakGlass(
                Map.of("reason", "Unresponsive trauma", "patientId", "cpid-1"),
                TENANT,
                "provider-1");

        assertEquals(201, response.getStatusCode().value());
        assertEquals(true, response.getBody().get("success"));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("id"));
        verify(authzClient).requestBreakGlass(any());
    }
}
