package zw.gov.mohcc.impilo.clinical.assistant.retrieval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.clinical.persistence.repository.SourceSectionRepository;

import java.util.Locale;
import java.util.Set;

/**
 * Selects the single {@link GuidanceRetriever} bean by {@code impilo.clinical.retrieval.mode}.
 * {@code semantic}/{@code hybrid} use search-service (fail-closed to lexical); anything else
 * (default {@code like}) uses the lexical retriever directly.
 *
 * <p>Default is {@code like} because semantic retrieval has a hard data prerequisite: EDLIZ sections
 * must be indexed into search-service (with an embeddings provider configured). Flip to {@code hybrid}
 * once that back-fill has run; until then the semantic retriever would simply fall back to lexical.
 */
@Configuration
public class RetrievalConfig {

    private static final Logger log = LoggerFactory.getLogger(RetrievalConfig.class);
    private static final Set<String> SEMANTIC_MODES = Set.of("semantic", "hybrid");

    @Bean
    public GuidanceRetriever guidanceRetriever(
            @Value("${impilo.clinical.retrieval.mode:like}") String mode,
            @Value("${impilo.clinical.search.base-url:http://localhost:8230}") String searchBaseUrl,
            SourceSectionRepository sectionRepository,
            RestTemplate clinicalLlmRestTemplate) {
        LikeGuidanceRetriever lexical = new LikeGuidanceRetriever(sectionRepository);
        String m = mode == null ? "like" : mode.trim().toLowerCase(Locale.ROOT);
        if (SEMANTIC_MODES.contains(m)) {
            log.info("Guidance retrieval: {} (semantic via search-service, fail-closed to lexical).", m);
            return new SemanticGuidanceRetriever(clinicalLlmRestTemplate, sectionRepository, searchBaseUrl, m, lexical);
        }
        log.info("Guidance retrieval: lexical (LIKE).");
        return lexical;
    }
}
