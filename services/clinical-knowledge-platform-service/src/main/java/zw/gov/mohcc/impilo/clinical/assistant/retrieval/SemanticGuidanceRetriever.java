package zw.gov.mohcc.impilo.clinical.assistant.retrieval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.clinical.persistence.entity.SourceSectionEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.SourceSectionRepository;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Semantic / hybrid retriever backed by search-service (embeddings + pgvector). Queries the indexed
 * {@code edliz_section} entities, resolves the returned entity ids back to {@link SourceSectionEntity}
 * rows (preserving rank order), and so returns the exact same shape as the lexical retriever.
 *
 * <p>Fail-closed: any failure — search-service unreachable, no index yet, empty result, malformed
 * response — delegates to the lexical {@link GuidanceRetriever} fallback. There is therefore no
 * regression if the EDLIZ semantic index has not been back-filled; retrieval simply stays lexical.
 */
public class SemanticGuidanceRetriever implements GuidanceRetriever {

    private static final Logger log = LoggerFactory.getLogger(SemanticGuidanceRetriever.class);
    private static final String ENTITY_TYPE = "edliz_section";

    private final RestTemplate restTemplate;
    private final SourceSectionRepository sectionRepository;
    private final String searchBaseUrl;
    private final String rankMode;
    private final GuidanceRetriever lexicalFallback;

    public SemanticGuidanceRetriever(
            RestTemplate restTemplate,
            SourceSectionRepository sectionRepository,
            String searchBaseUrl,
            String rankMode,
            GuidanceRetriever lexicalFallback) {
        this.restTemplate = restTemplate;
        this.sectionRepository = sectionRepository;
        this.searchBaseUrl = searchBaseUrl;
        this.rankMode = rankMode;
        this.lexicalFallback = lexicalFallback;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<SourceSectionEntity> retrieve(String query, int maxResults) {
        try {
            String url = searchBaseUrl + "/internal/v1/search?q="
                    + URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8)
                    + "&entityType=" + ENTITY_TYPE
                    + "&size=" + Math.max(1, maxResults)
                    + "&rankMode=" + rankMode;

            ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = resp.getBody();
            List<Map<String, Object>> results = body == null ? null : (List<Map<String, Object>>) body.get("results");
            if (results == null || results.isEmpty()) {
                return lexicalFallback.retrieve(query, maxResults);
            }

            List<UUID> ids = new ArrayList<>();
            for (Map<String, Object> r : results) {
                Object eid = r.get("entityId");
                if (eid != null) {
                    try {
                        ids.add(UUID.fromString(eid.toString()));
                    } catch (IllegalArgumentException ignored) {
                        // non-UUID entity id — skip
                    }
                }
            }
            if (ids.isEmpty()) {
                return lexicalFallback.retrieve(query, maxResults);
            }

            Map<UUID, SourceSectionEntity> byId = sectionRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(SourceSectionEntity::getId, s -> s));
            List<SourceSectionEntity> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
            return ordered.isEmpty() ? lexicalFallback.retrieve(query, maxResults) : ordered;
        } catch (Exception e) {
            log.warn("Semantic retrieval failed ({}) — falling back to lexical: {}", rankMode, e.getMessage());
            return lexicalFallback.retrieve(query, maxResults);
        }
    }
}
