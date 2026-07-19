package zw.gov.mohcc.impilo.experience.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.config.LearningServiceRuntimeProperties;

/**
 * Live-caught regression (2026-07-19): write failures were swallowed into
 * null → the controller answered 200 {"data":{}} and browser create/enrol
 * silently no-opped. Writes must propagate upstream status + message; reads
 * keep the soft-null degrade.
 */
class LearningServiceClientTest {

    private static LearningServiceRuntimeProperties props() {
        LearningServiceRuntimeProperties p = new LearningServiceRuntimeProperties();
        p.setBaseUrl("http://learning:8235");
        return p;
    }

    @Test
    void postV11PropagatesUpstreamStatusAndErrorMessage() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        LearningServiceClient client = new LearningServiceClient(restTemplate, props());

        server.expect(requestTo("http://learning:8235/internal/v1/learning/v11/enrolments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"INVALID_SUBJECT_TYPE\",\"message\":\"subjectType must be one of [...]\"}}"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> client.postV11("enrolments", Map.of("courseId", "c1")));
        assertEquals(HttpStatus.BAD_REQUEST.value(), ex.getStatusCode().value());
        assertEquals("subjectType must be one of [...]", ex.getReason());
        server.verify();
    }

    @Test
    void postV11MapsOpaqueUpstream500ToStatusWithGenericReason() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        LearningServiceClient client = new LearningServiceClient(restTemplate, props());

        server.expect(requestTo("http://learning:8235/internal/v1/learning/v11/catalog"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> client.postV11("catalog", Map.of("title", "t")));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getStatusCode().value());
        server.verify();
    }

    @Test
    void putV11PropagatesFailuresLikePost() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        LearningServiceClient client = new LearningServiceClient(restTemplate, props());

        server.expect(requestTo("http://learning:8235/internal/v1/learning/v11/catalog/c1"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"status transition not allowed\"}}"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> client.putV11("catalog/c1", Map.of("status", "PUBLISHED")));
        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        assertEquals("status transition not allowed", ex.getReason());
        server.verify();
    }

    @Test
    void postV11StillReturnsUnwrappedDataOnSuccess() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        LearningServiceClient client = new LearningServiceClient(restTemplate, props());

        server.expect(requestTo("http://learning:8235/internal/v1/learning/v11/catalog"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"data\":{\"course\":{\"id\":\"c9\"}}}", MediaType.APPLICATION_JSON));

        assertEquals("c9", client.postV11("catalog", Map.of("title", "t")).get("course").get("id").asText());
        server.verify();
    }

    @Test
    void getV11KeepsTheSoftNullDegradeForReads() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        LearningServiceClient client = new LearningServiceClient(restTemplate, props());

        server.expect(requestTo("http://learning:8235/internal/v1/learning/v11/catalog"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        assertNull(client.getV11("catalog", Map.of()));
        server.verify();
    }
}
