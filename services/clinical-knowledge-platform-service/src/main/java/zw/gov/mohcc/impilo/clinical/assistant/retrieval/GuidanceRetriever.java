package zw.gov.mohcc.impilo.clinical.assistant.retrieval;

import zw.gov.mohcc.impilo.clinical.persistence.entity.SourceSectionEntity;

import java.util.List;

/**
 * Retrieves the EDLIZ guidance sections that ground a clinical answer. Implementations are swappable
 * by {@code impilo.clinical.retrieval.mode} (lexical LIKE vs semantic/hybrid) and all return the same
 * {@link SourceSectionEntity} shape, so the assistant's downstream composition/citation logic — and
 * the response contract — are identical regardless of retrieval mode.
 */
public interface GuidanceRetriever {

    List<SourceSectionEntity> retrieve(String query, int maxResults);
}
