package zw.gov.mohcc.impilo.experience.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ButanoServiceClientTest {

    @Test
    void getIpsSummaryCallsButanoSummaryEndpoint() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        ServiceClientConfig.ServiceEndpoints endpoints = new ServiceClientConfig.ServiceEndpoints(
                "http://pct", "http://oros", "http://pharmacy", "http://butano",
                "http://msika", "http://msika-flow", "http://mushex", "http://vito",
                "http://tuso", "http://varapi", "http://documents", "http://costa",
                "http://coverage", "http://surveillance", "http://campaigns", "http://indawo",
                "http://governance", "http://landela", "http://notifications",
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null
        );
        ButanoServiceClient client = new ButanoServiceClient(restTemplate, endpoints);

        server.expect(requestTo("http://butano/v1/summary/ips/ZW-CPID-001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"resourceType\":\"Bundle\",\"type\":\"document\"}",
                        MediaType.parseMediaType("application/fhir+json")
                ));

        ResponseEntity<String> response = client.getIpsSummary("ZW-CPID-001");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("application/fhir+json", response.getHeaders().getContentType().toString());
        assertEquals("{\"resourceType\":\"Bundle\",\"type\":\"document\"}", response.getBody());
        server.verify();
    }
}
