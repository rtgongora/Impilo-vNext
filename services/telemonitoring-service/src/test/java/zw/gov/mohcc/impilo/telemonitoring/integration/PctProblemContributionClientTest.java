package zw.gov.mohcc.impilo.telemonitoring.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Wave 5.2 — contributes a monitoring indication into pct_problems with
 * {@code responsible_service=telemonitoring}, best-effort on outage.
 */
class PctProblemContributionClientTest {

    private static final UUID TENANT = UUID.randomUUID();

    private RestTemplate rt;
    private MockRestServiceServer server;
    private PctProblemContributionClient client;

    @BeforeEach
    void setUp() {
        rt = new RestTemplate();
        server = MockRestServiceServer.bindTo(rt).build();
        client = new PctProblemContributionClient(rt, "http://pct-service");
        TrustContextHolder.set(new TrustContext(TENANT, "actor-clinician", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private JsonNode capturedPayload(String rawRequestBody) throws java.io.IOException {
        return new ObjectMapper().readTree(rawRequestBody);
    }

    @Test
    void contributesWithResponsibleServiceTelemonitoring() throws Exception {
        UUID problemId = UUID.randomUUID();
        server.expect(requestTo("http://pct-service/v1/problems"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(request -> {
                    JsonNode payload = capturedPayload(request.getBody().toString());
                    assertThat(payload.get("responsible_service").asText()).isEqualTo("telemonitoring");
                    assertThat(payload.get("subject_cpid").asText()).isEqualTo("CPID-1");
                    assertThat(payload.get("display").asText()).isEqualTo("Essential hypertension");
                    assertThat(payload.get("category").asText()).isEqualTo("DIAGNOSIS");
                    assertThat(payload.get("diagnostic_certainty").asText()).isEqualTo("PROVISIONAL");
                    return withSuccess("{\"data\":{\"problem_id\":\"" + problemId + "\"}}", MediaType.APPLICATION_JSON)
                            .createResponse(request);
                });

        PctProblemContributionClient.ContributionResult result = client.contributeCondition(
                "CPID-1", null, null, "Essential hypertension", "PROVISIONAL",
                "Telemonitoring plan abc (programme HYPERTENSION)");

        assertThat(result.contributed()).isTrue();
        assertThat(result.problemId()).isEqualTo(problemId);
        server.verify();
    }

    @Test
    void returnsUnavailableRatherThanThrowingWhenPctIsDown() {
        server.expect(requestTo("http://pct-service/v1/problems"))
                .andRespond(withServerError());

        PctProblemContributionClient.ContributionResult result =
                client.contributeCondition("CPID-1", null, null, "Essential hypertension", null, null);

        assertThat(result.contributed()).isFalse();
        assertThat(result.problemId()).isNull();
    }

    @Test
    void aMissingDisplayNeverCallsPct() {
        PctProblemContributionClient.ContributionResult result =
                client.contributeCondition("CPID-1", null, null, "", null, null);

        assertThat(result.contributed()).isFalse();
        server.verify();
    }

    @Test
    void aMissingSubjectCpidNeverCallsPct() {
        PctProblemContributionClient.ContributionResult result =
                client.contributeCondition(null, null, null, "Essential hypertension", null, null);

        assertThat(result.contributed()).isFalse();
        server.verify();
    }
}
