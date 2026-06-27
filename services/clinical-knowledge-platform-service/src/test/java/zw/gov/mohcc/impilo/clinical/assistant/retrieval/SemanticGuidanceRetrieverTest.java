package zw.gov.mohcc.impilo.clinical.assistant.retrieval;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.clinical.persistence.entity.SourceSectionEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.SourceSectionRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticGuidanceRetrieverTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final SourceSectionRepository repo = mock(SourceSectionRepository.class);
    private final GuidanceRetriever fallback = mock(GuidanceRetriever.class);

    private SemanticGuidanceRetriever retriever() {
        return new SemanticGuidanceRetriever(restTemplate, repo, "http://search", "hybrid", fallback);
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolvesSearchHitsToSectionsInRankOrder() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Map<String, Object> body = Map.of("results", List.of(
                Map.of("entityId", id1.toString()), Map.of("entityId", id2.toString())));
        when(restTemplate.getForEntity(anyString(), eq(Map.class))).thenReturn(ResponseEntity.ok(body));

        SourceSectionEntity e1 = mock(SourceSectionEntity.class);
        SourceSectionEntity e2 = mock(SourceSectionEntity.class);
        when(e1.getId()).thenReturn(id1);
        when(e2.getId()).thenReturn(id2);
        when(repo.findAllById(any())).thenReturn(List.of(e2, e1)); // repo returns unordered

        List<SourceSectionEntity> out = retriever().retrieve("cough", 5);

        assertThat(out).containsExactly(e1, e2); // rank order from search preserved
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyResults_fallBackToLexical() {
        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("results", List.of())));
        SourceSectionEntity lexical = mock(SourceSectionEntity.class);
        when(fallback.retrieve(anyString(), anyInt())).thenReturn(List.of(lexical));

        assertThat(retriever().retrieve("cough", 5)).containsExactly(lexical);
        verify(fallback).retrieve("cough", 5);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchServiceError_failsClosedToLexical() {
        when(restTemplate.getForEntity(anyString(), eq(Map.class))).thenThrow(new RestClientException("down"));
        SourceSectionEntity lexical = mock(SourceSectionEntity.class);
        when(fallback.retrieve(anyString(), anyInt())).thenReturn(List.of(lexical));

        assertThat(retriever().retrieve("cough", 5)).containsExactly(lexical);
        verify(fallback).retrieve("cough", 5);
    }
}
