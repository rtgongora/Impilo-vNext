package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The find-care distance-matrix call must carry a service-originated actor.
 *
 * <p>Its only caller is the <b>anonymous</b> public find-care lane, whose trust headers are
 * stripped at the edge, and the shared forwarding interceptor forwards an actor rather than
 * synthesising one — so nothing supplies an actor unless this client method does. Ndila's Tshepo
 * guard denies its INTERNAL routing operations outright when the actor is absent, and returns that
 * denial as HTTP 200 with {@code success:false}, which
 * {@code FindCareOrchestrationService.applyDistances} reads as "no route" before falling back to
 * straight-line distance with a null ETA.</p>
 *
 * <p>This pins the half of the fix that lives on the calling side. A bearer token does not satisfy
 * the guard — it reads the actor headers, never the JWT — so giving this call a valid service token
 * did not, on its own, restore ETAs.</p>
 */
class NdilaDistanceMatrixActorHeaderTest {

    private static final String MATRIX_URL =
            ServiceClientConfig.testEndpointsStandardWireMocks().ndilaBaseUrl()
                    + "/api/v1/ndila/distance-matrix";

    @Test
    void distanceMatrixSendsAServiceOriginatedActor() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        NdilaServiceClient client = new NdilaServiceClient(
                restTemplate,
                ServiceClientConfig.testEndpointsStandardWireMocks(),
                new ObjectMapper());

        server.expect(requestTo(MATRIX_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Actor-ID", "public-gateway"))
                .andExpect(header("X-Actor-Type", "SYSTEM"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(
                        "{\"success\":true,\"distancesMeters\":[[4200.0]],\"durationsSeconds\":[[480.0]]}",
                        MediaType.APPLICATION_JSON));

        client.distanceMatrixFromOrigin(
                new BigDecimal("-17.82"), new BigDecimal("31.05"),
                List.of(Map.of("latitude", new BigDecimal("-17.83"),
                        "longitude", new BigDecimal("31.06"))));

        server.verify();
    }
}
