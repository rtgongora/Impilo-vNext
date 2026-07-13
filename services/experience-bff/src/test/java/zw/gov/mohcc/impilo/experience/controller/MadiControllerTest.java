package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import zw.gov.mohcc.impilo.experience.client.MadiServiceClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MadiControllerTest {

    @Test
    void dashboard_delegatesToMadiService() {
        MadiServiceClient client = mock(MadiServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode metrics = mapper.createObjectNode();
        metrics.put("active_drives", 3);
        when(client.dashboard("local", null, null)).thenReturn(metrics);

        MadiController controller = new MadiController(client);

        var response = controller.dashboard(
                "local", null, null, "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        verify(client).dashboard("local", null, null);
    }

    @Test
    void registerDonor_delegatesToMadiService() {
        MadiServiceClient client = mock(MadiServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode created = mapper.createObjectNode();
        created.put("id", "d1000000-0000-0000-0000-000000000001");
        when(client.registerDonor(any())).thenReturn(created);

        MadiController controller = new MadiController(client);

        var response = controller.registerDonor(
                Map.of("person_cpid", "CPID-001"), "req-1", "corr-1");

        assertEquals(201, response.getStatusCode().value());
        verify(client).registerDonor(any());
    }

    @Test
    void donorByPerson_delegatesToMadiService() {
        MadiServiceClient client = mock(MadiServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode profile = mapper.createObjectNode();
        profile.put("person_cpid", "CPID-001");
        when(client.donorByPerson("CPID-001")).thenReturn(profile);

        MadiController controller = new MadiController(client);

        var response = controller.donorByPerson("CPID-001", "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        verify(client).donorByPerson(eq("CPID-001"));
    }

    @Test
    void getOrder_delegatesToMadiService() {
        MadiServiceClient client = mock(MadiServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode order = mapper.createObjectNode();
        order.put("order_id", "o1000000-0000-0000-0000-000000000001");
        when(client.getOrder("o1000000-0000-0000-0000-000000000001")).thenReturn(order);

        MadiController controller = new MadiController(client);

        var response = controller.getOrder(
                "o1000000-0000-0000-0000-000000000001", "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        verify(client).getOrder(eq("o1000000-0000-0000-0000-000000000001"));
    }

    @Test
    void preVerifyTransfusion_delegatesToMadiService() {
        MadiServiceClient client = mock(MadiServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode verified = mapper.createObjectNode();
        verified.put("verified", true);
        when(client.preVerifyTransfusion(any(), any())).thenReturn(verified);

        MadiController controller = new MadiController(client);

        var response = controller.preVerifyTransfusion(
                "e1000000-0000-0000-0000-000000000001",
                Map.of("patient_cpid", "CPID-001", "blood_unit_id", "UNIT-001"),
                "req-1",
                "corr-1");

        assertEquals(200, response.getStatusCode().value());
        verify(client).preVerifyTransfusion(eq("e1000000-0000-0000-0000-000000000001"), any());
    }

    @Test
    void nationalHaemovigilanceDashboard_delegatesToMadiService() {
        MadiServiceClient client = mock(MadiServiceClient.class);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode dashboard = mapper.createObjectNode();
        dashboard.put("open_cases", 2);
        when(client.nationalHaemovigilanceDashboard()).thenReturn(dashboard);

        MadiController controller = new MadiController(client);

        var response = controller.nationalHaemovigilanceDashboard("req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        verify(client).nationalHaemovigilanceDashboard();
    }

    @Test
    void donorByPerson_treatsUpstream404AsEmptyDonor() {
        // QA #14: a non-donor lookup must surface as 200 {data:null}, not 502.
        MadiServiceClient client = mock(MadiServiceClient.class);
        when(client.donorByPerson("CPID-404")).thenThrow(
                HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

        MadiController controller = new MadiController(client);
        var response = controller.donorByPerson("CPID-404", "req-1", "corr-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNull(response.getBody().get("data"));
    }

    @Test
    void registerDonor_passesUpstream409ConflictThrough() {
        // QA #13: a duplicate donor (409) must reach the caller with the real
        // message, not be masked as a 502 MADI_UNAVAILABLE.
        MadiServiceClient client = mock(MadiServiceClient.class);
        byte[] body = "{\"error\":\"Donor already registered for person\"}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(client.registerDonor(any())).thenThrow(
                HttpClientErrorException.create(
                        HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, body, null));

        MadiController controller = new MadiController(client);
        var response = controller.registerDonor(Map.of("person_cpid", "CPID-DUP"), "req-1", "corr-1");

        assertEquals(409, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertEquals("Donor already registered for person", error.get("message"));
    }
}
