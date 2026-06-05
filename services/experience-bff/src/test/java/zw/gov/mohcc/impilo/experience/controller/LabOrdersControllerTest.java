package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LabOrdersControllerTest {

    @Test
    void createLabOrder_delegatesToOrosWithTypedContract() {
        OrosServiceClient orosClient = mock(OrosServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode orosResponse = mapper.createObjectNode();
        orosResponse.put("orderId", "oros-order-99");
        when(orosClient.placeOrder(
                eq("LAB"),
                eq("STAT"),
                eq("CPID-ZW-00001"),
                eq("enc-42"),
                isNull(),
                anyList()))
                .thenReturn(orosResponse);

        LabOrdersController controller = new LabOrdersController(orosClient);

        var response = controller.createLabOrder(
                "tenant-1",
                "pod-1",
                "req-1",
                "corr-1",
                "idem-1",
                new LabOrdersController.CreateLabOrderRequest(
                        "patient-1",
                        "enc-42",
                        "Troponin",
                        "TROP",
                        "LABORATORY",
                        "STAT",
                        null,
                        "user-1",
                        "Dr. Moyo",
                        "facility-1",
                        "CPID-ZW-00001",
                        "enc-42"
                )
        );

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
        assertEquals("ORDERED", attributes.get("status"));
        assertEquals("enc-42", attributes.get("encounter_id"));
        assertEquals("oros-order-99", attributes.get("oros_order_id"));
        verify(orosClient).placeOrder(eq("LAB"), eq("STAT"), eq("CPID-ZW-00001"), eq("enc-42"), isNull(), anyList());
    }
}
