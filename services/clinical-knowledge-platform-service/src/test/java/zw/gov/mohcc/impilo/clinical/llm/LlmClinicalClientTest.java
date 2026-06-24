package zw.gov.mohcc.impilo.clinical.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LlmClinicalClientTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private LlmClinicalClient client;

    private static final String URL = "http://llm/internal/v1/llm/structured";

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new LlmClinicalClient(restTemplate, "http://llm", "anthropic", 1200);
    }

    private Optional<LlmClinicalClient.LlmStructuredResult> call() {
        return client.requestStructured("UC", List.of(Map.of("role", "user", "content", "q")),
                Map.of("type", "object"), Map.of(), "HIGH", 0.1);
    }

    @Test
    void realProviderWithStructuredOutput_isReturned() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"provider\":\"anthropic\",\"model\":\"claude\",\"structuredOutput\":{\"answer_summary\":\"x\"},\"fallbackUsed\":false,\"auditRef\":\"a1\"}",
                MediaType.APPLICATION_JSON));

        var r = call();
        assertThat(r).isPresent();
        assertThat(r.get().provider()).isEqualTo("anthropic");
        assertThat(r.get().structuredOutput()).containsKey("answer_summary");
        server.verify();
    }

    @Test
    void stubProvider_isTreatedAsNoLlm() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"provider\":\"deterministic\",\"model\":\"deterministic\",\"structuredOutput\":{\"answer_summary\":\"x\"},\"fallbackUsed\":true}",
                MediaType.APPLICATION_JSON));
        assertThat(call()).isEmpty();
    }

    @Test
    void emptyStructuredOutput_isTreatedAsNoLlm() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"provider\":\"anthropic\",\"model\":\"claude\",\"structuredOutput\":{}}", MediaType.APPLICATION_JSON));
        assertThat(call()).isEmpty();
    }

    @Test
    void serverError_isTreatedAsNoLlm() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThat(call()).isEmpty();
    }
}
