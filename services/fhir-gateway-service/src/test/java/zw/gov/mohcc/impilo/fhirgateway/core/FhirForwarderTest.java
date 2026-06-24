package zw.gov.mohcc.impilo.fhirgateway.core;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Proves the forwarder actually POSTs the payload and reports delivery only on a 2xx response. */
class FhirForwarderTest {

    private static final String TARGET = "http://butano/fhir/Patient";
    private static final String PAYLOAD = "{\"resourceType\":\"Patient\"}";

    @Test
    void delivered_on2xx() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(TARGET)).andExpect(method(POST)).andExpect(content().string(PAYLOAD))
                .andRespond(withSuccess("{\"id\":\"1\"}", APPLICATION_JSON));

        FhirForwarder.ForwardAttempt attempt =
                new FhirForwarder(rt).send(TARGET, "Patient", "CREATE", PAYLOAD);

        assertThat(attempt.delivered()).isTrue();
        server.verify();
    }

    @Test
    void notDelivered_onServerError() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(TARGET)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        FhirForwarder.ForwardAttempt attempt =
                new FhirForwarder(rt).send(TARGET, "Patient", "CREATE", PAYLOAD);

        assertThat(attempt.delivered()).isFalse();
        assertThat(attempt.status()).isEqualTo(500);
    }

    @Test
    void notDelivered_onClientError() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(TARGET)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThat(new FhirForwarder(rt).send(TARGET, "Patient", "CREATE", PAYLOAD).delivered()).isFalse();
    }
}
