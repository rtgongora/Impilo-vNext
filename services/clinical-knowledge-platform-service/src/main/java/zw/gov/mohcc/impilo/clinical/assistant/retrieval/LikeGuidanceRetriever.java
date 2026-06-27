package zw.gov.mohcc.impilo.clinical.assistant.retrieval;

import org.springframework.data.domain.PageRequest;
import zw.gov.mohcc.impilo.clinical.persistence.entity.SourceSectionEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.SourceSectionRepository;

import java.util.List;

/**
 * Lexical (SQL LIKE) retriever — the original, always-available behaviour and the fail-closed
 * fallback for the semantic retriever. Needs no external infrastructure.
 */
public class LikeGuidanceRetriever implements GuidanceRetriever {

    private final SourceSectionRepository sectionRepository;

    public LikeGuidanceRetriever(SourceSectionRepository sectionRepository) {
        this.sectionRepository = sectionRepository;
    }

    @Override
    public List<SourceSectionEntity> retrieve(String query, int maxResults) {
        return sectionRepository.searchActive(query, PageRequest.of(0, Math.max(1, maxResults)));
    }
}
