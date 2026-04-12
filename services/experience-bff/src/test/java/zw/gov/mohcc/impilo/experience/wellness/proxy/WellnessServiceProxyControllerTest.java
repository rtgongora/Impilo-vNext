package zw.gov.mohcc.impilo.experience.wellness.proxy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Contract-style tests: stable BFF paths must forward to {@code wellness-service} with the same URI and
 * trust headers (Wave 1 quality bar).
 */
class WellnessServiceProxyControllerTest {

    private static final String DOWNSTREAM = "http://127.0.0.1:9";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private WellnessServiceProxyController controller;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        controller = new WellnessServiceProxyController(restTemplate, DOWNSTREAM);
    }

    @AfterEach
    void verifyDownstream() {
        server.verify();
    }

    @Test
    void forwardsGetWellnessActivitiesWithQueryAndTenantHeader() {
        server.expect(requestTo(DOWNSTREAM + "/internal/v1/mobile/citizen/wellness/activities?patientId=CPID-1&days=7"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Tenant-ID", "tenant-a"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/internal/v1/mobile/citizen/wellness/activities");
        req.setQueryString("patientId=CPID-1&days=7");
        req.addHeader("X-Tenant-ID", "tenant-a");

        var response = controller.forward(req, null);
        assertEquals(200, response.getStatusCode().value());
        assertArrayEquals("{\"data\":[]}".getBytes(StandardCharsets.UTF_8), response.getBody());
    }

    @Test
    void forwardsMonitoringDevicesList() {
        server.expect(requestTo(DOWNSTREAM + "/internal/v1/mobile/citizen/monitoring/devices?patientId=CPID-2"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Tenant-ID", "tenant-b"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/internal/v1/mobile/citizen/monitoring/devices");
        req.setQueryString("patientId=CPID-2");
        req.addHeader("X-Tenant-ID", "tenant-b");

        var response = controller.forward(req, null);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void forwardsHealthConnectChangesetPostBody() {
        String json =
                "{\"patientId\":\"P1\",\"dataOrigin\":{\"platform\":\"android_health_connect\"},\"records\":[]}";
        server.expect(requestTo(DOWNSTREAM + "/internal/v1/wellness/connect/v1/changesets"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Tenant-ID", "tenant-c"))
                .andExpect(content().string(json))
                .andRespond(
                        withSuccess("{\"data\":{\"applied\":0,\"skipped\":0,\"errors\":[]}}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setRequestURI("/internal/v1/wellness/connect/v1/changesets");
        req.addHeader("X-Tenant-ID", "tenant-c");
        req.addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        var response = controller.forward(req, json.getBytes(StandardCharsets.UTF_8));
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void forwardsPropagates4xxFromWellnessService() {
        server.expect(requestTo(DOWNSTREAM + "/internal/v1/mobile/citizen/wellness/vitals?patientId=missing"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"error\":\"not_found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("GET");
        req.setRequestURI("/internal/v1/mobile/citizen/wellness/vitals");
        req.setQueryString("patientId=missing");

        var response = controller.forward(req, null);
        assertEquals(404, response.getStatusCode().value());
    }
}
