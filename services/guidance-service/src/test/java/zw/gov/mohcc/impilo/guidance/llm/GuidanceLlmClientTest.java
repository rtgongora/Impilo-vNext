package zw.gov.mohcc.impilo.guidance.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies grounded LLM synthesis and its honesty gate / fail-closed behaviour (G039).
 */
@ExtendWith(MockitoExtension.class)
class GuidanceLlmClientTest {

    @Mock
    private RestTemplate restTemplate;

    private static final List<GuidanceLlmClient.Source> SOURCES = List.of(
            new GuidanceLlmClient.Source("Malaria prevention", "Use treated nets and seek care for fever.", "PUBLIC_HEALTH"));

    private GuidanceLlmClient client(boolean enabled) {
        return new GuidanceLlmClient(restTemplate, enabled, "http://llm:8265", "anthropic", 800);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stub(Map<String, Object> body) {
        when(restTemplate.postForEntity(eq("http://llm:8265/internal/v1/llm/structured"), any(), eq(Map.class)))
                .thenReturn((ResponseEntity) ResponseEntity.ok(body));
    }

    @Test
    void realProviderWithStructuredAnswerIsUsed() {
        stub(Map.of(
                "provider", "anthropic",
                "model", "claude",
                "structuredOutput", Map.of(
                        "answer", "Sleep under a treated net and seek care for fever.",
                        "confidence", 0.9,
                        "citedTitles", List.of("Malaria prevention"))));

        Optional<GuidanceLlmClient.GroundedAnswer> res = client(true).synthesize("How do I prevent malaria?", SOURCES);

        assertThat(res).isPresent();
        assertThat(res.get().answer()).contains("treated net");
        assertThat(res.get().confidence()).isEqualTo(0.9);
        assertThat(res.get().citedTitles()).containsExactly("Malaria prevention");
    }

    @Test
    void nonRealProviderFallsBack() {
        stub(Map.of(
                "provider", "deterministic",
                "structuredOutput", Map.of("answer", "anything")));
        assertThat(client(true).synthesize("q", SOURCES)).isEmpty();
    }

    @Test
    void emptyStructuredOutputFallsBack() {
        stub(Map.of("provider", "anthropic", "structuredOutput", Map.of()));
        assertThat(client(true).synthesize("q", SOURCES)).isEmpty();
    }

    @Test
    void confidenceIsClampedToUnitInterval() {
        stub(Map.of("provider", "openai",
                "structuredOutput", Map.of("answer", "ok", "confidence", 4.2)));
        assertThat(client(true).synthesize("q", SOURCES).orElseThrow().confidence()).isEqualTo(1.0);
    }

    @Test
    void disabledDoesNotCallTheLlm() {
        Optional<GuidanceLlmClient.GroundedAnswer> res = client(false).synthesize("q", SOURCES);
        assertThat(res).isEmpty();
        verify(restTemplate, never()).postForEntity(any(String.class), any(), any());
    }

    @Test
    void emptySourcesDoesNotCallTheLlm() {
        assertThat(client(true).synthesize("q", List.of())).isEmpty();
        verify(restTemplate, never()).postForEntity(any(String.class), any(), any());
    }

    @Test
    void upstreamErrorFailsClosed() {
        lenient().when(restTemplate.postForEntity(any(String.class), any(), eq(Map.class)))
                .thenThrow(new RestClientException("connection refused"));
        assertThat(client(true).synthesize("q", SOURCES)).isEmpty();
    }
}
