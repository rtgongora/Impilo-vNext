package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Verifies the BFF patient-safety client targets the canonical internal routes and unwraps {data}. */
class PatientSafetyServiceClientTest {

    private static final String BASE = "http://localhost:8202/internal/v1/patient-safety";

    private PatientSafetyServiceClient client(RestTemplate rt) {
        return new PatientSafetyServiceClient(rt, ServiceClientConfig.testEndpointsStandardWireMocks());
    }

    @Test
    void createReportPostsToReportsRouteAndUnwrapsData() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/reports"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"data\":{\"id\":\"r1\",\"status\":\"DRAFT\"}}", MediaType.APPLICATION_JSON));

        JsonNode data = client(rt).createReport(Map.of("reportType", "ADR", "reporterKind", "PROVIDER"));
        assertEquals("r1", data.get("id").asText());
        server.verify();
    }

    @Test
    void submitReportPostsToSubmitRoute() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/reports/r1/submit"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"data\":{\"status\":\"SUBMITTED\",\"caseReference\":\"PSC-2026-000001\"}}",
                        MediaType.APPLICATION_JSON));

        JsonNode data = client(rt).submitReport("r1");
        assertEquals("PSC-2026-000001", data.get("caseReference").asText());
        server.verify();
    }

    @Test
    void markExportReadyPostsToCanonicalRoute() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/cases/c1/mark-export-ready"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"data\":{\"status\":\"EXPORT_READY\",\"latestExport\":{\"adapterEnabled\":false}}}",
                        MediaType.APPLICATION_JSON));

        JsonNode data = client(rt).markExportReady("c1");
        assertEquals("EXPORT_READY", data.get("status").asText());
        assertEquals(false, data.get("latestExport").get("adapterEnabled").asBoolean());
        server.verify();
    }

    @Test
    void adaptersGetUsesConfigRoute() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(BASE + "/config/adapters"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"data\":[{\"key\":\"vigiflow-e2b\",\"enabled\":false}]}",
                        MediaType.APPLICATION_JSON));

        JsonNode data = client(rt).adapters();
        assertEquals("vigiflow-e2b", data.get(0).get("key").asText());
        server.verify();
    }
}
