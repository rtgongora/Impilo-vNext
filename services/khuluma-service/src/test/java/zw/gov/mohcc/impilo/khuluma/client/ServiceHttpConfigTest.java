package zw.gov.mohcc.impilo.khuluma.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The outbound trust-header interceptor must synthesize the full v1.1 mandatory set even
 * when no inbound RequestContext exists (service-originated calls), and must add an
 * {@code Idempotency-Key} on command methods — downstream companion filters (rtc-gateway,
 * live-service) fail-closed with MISSING_REQUIRED_HEADER / IDEMPOTENCY_KEY_REQUIRED
 * otherwise, which silently degraded khuluma's media provisioning to "unavailable".
 */
class ServiceHttpConfigTest {

    private RestTemplate templateUnderTest() {
        ServiceHttpConfig config = new ServiceHttpConfig();
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(java.util.List.of(config.trustHeaderForwardingInterceptor()));
        return restTemplate;
    }

    @Test
    void post_synthesizesPodRequestAndIdempotencyHeaders() {
        RestTemplate restTemplate = templateUnderTest();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        server.expect(requestTo("http://downstream/internal/v1/rtc/sessions"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("X-Pod-ID", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString())))
                .andExpect(header("X-Request-ID", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString())))
                .andExpect(header("X-Correlation-ID", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString())))
                .andExpect(header("Idempotency-Key", org.hamcrest.Matchers.startsWith("khuluma:")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        restTemplate.postForEntity("http://downstream/internal/v1/rtc/sessions",
                java.util.Map.of("k", "v"), String.class);
        server.verify();
    }

    @Test
    void get_doesNotAddIdempotencyKey() {
        RestTemplate restTemplate = templateUnderTest();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        server.expect(requestTo("http://downstream/internal/v1/rtc/sessions/s-1"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andExpect(header("X-Request-ID", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyOrNullString())))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        restTemplate.getForEntity("http://downstream/internal/v1/rtc/sessions/s-1", String.class);
        server.verify();
    }
}
