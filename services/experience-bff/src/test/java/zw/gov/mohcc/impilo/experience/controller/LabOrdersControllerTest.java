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

    @Test
    void createLabOrder_mapsImagingCategoryToOrosImagingType() {
        OrosServiceClient orosClient = mock(OrosServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode orosResponse = mapper.createObjectNode();
        orosResponse.put("orderId", "oros-img-1");
        when(orosClient.placeOrder(
                eq("IMAGING"),
                eq("URGENT"),
                eq("CPID-ZW-00002"),
                eq("enc-99"),
                isNull(),
                anyList()))
                .thenReturn(orosResponse);

        LabOrdersController controller = new LabOrdersController(orosClient);

        var response = controller.createLabOrder(
                "tenant-1",
                "pod-1",
                "req-2",
                "corr-2",
                "idem-2",
                new LabOrdersController.CreateLabOrderRequest(
                        "patient-2",
                        "enc-99",
                        "Chest X-ray PA",
                        "CXR-PA",
                        "IMAGING",
                        "URGENT",
                        null,
                        "user-2",
                        "Dr. Ncube",
                        "facility-1",
                        "CPID-ZW-00002",
                        "enc-99"
                )
        );

        assertEquals(201, response.getStatusCode().value());
        verify(orosClient).placeOrder(eq("IMAGING"), eq("URGENT"), eq("CPID-ZW-00002"), eq("enc-99"), isNull(), anyList());
    }

    @Test
    void createLabOrder_returnsOrosOrderIdAsCanonicalId() {
        OrosServiceClient orosClient = mock(OrosServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode orosResponse = mapper.createObjectNode();
        orosResponse.put("orderId", "01ARZ3NDEKTSV4RRFFQ69G5FAV");
        when(orosClient.placeOrder(any(), any(), any(), any(), any(), anyList())).thenReturn(orosResponse);

        LabOrdersController controller = new LabOrdersController(orosClient);
        var response = controller.createLabOrder("t", "p", "r", "c", "i",
                new LabOrdersController.CreateLabOrderRequest("patient-1", "enc-1", "FBC", "FBC",
                        "LABORATORY", "ROUTINE", null, "user-1", "Dr X", "fac-1", "CPID-1", "enc-1"));

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        // The canonical id IS the sovereign OROS order id — so follow-on actions address the real order.
        assertEquals("01ARZ3NDEKTSV4RRFFQ69G5FAV", data.get("id"));
    }

    @Test
    void collectLabOrder_delegatesToOrosSpecimenLifecycle() {
        OrosServiceClient orosClient = mock(OrosServiceClient.class);
        LabOrdersController controller = new LabOrdersController(orosClient);

        var response = controller.collectLabOrder("01ARZ3NDEKTSV4RRFFQ69G5FAV",
                "t", "p", "r", "c", "i", Map.of("specimenType", "blood"));

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertEquals("COLLECTED", data.get("status"));
        verify(orosClient).collectSpecimen(eq("01ARZ3NDEKTSV4RRFFQ69G5FAV"), anyMap());
    }

    @Test
    void resolveOrosOrderType_mapsImagingAliases() {
        assertEquals("IMAGING", LabOrdersController.resolveOrosOrderType("IMAGING"));
        assertEquals("IMAGING", LabOrdersController.resolveOrosOrderType("radiology"));
        assertEquals("LAB", LabOrdersController.resolveOrosOrderType("LABORATORY"));
    }
}
