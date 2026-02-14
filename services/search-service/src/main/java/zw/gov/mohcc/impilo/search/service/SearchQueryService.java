package zw.gov.mohcc.impilo.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.search.api.dto.SearchRequest;
import zw.gov.mohcc.impilo.search.api.dto.SearchResponse;
import zw.gov.mohcc.impilo.search.domain.SearchDocumentEntity;
import zw.gov.mohcc.impilo.search.repository.SearchDocumentRepository;

import java.util.List;

@Service
public class SearchQueryService {

    private final SearchDocumentRepository documentRepository;
    private final ObjectMapper objectMapper;

    public SearchQueryService(SearchDocumentRepository documentRepository,
                              ObjectMapper objectMapper) {
        this.documentRepository = documentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(String tenantId, SearchRequest request) {
        PageRequest pageable = PageRequest.of(
                request.pageOrDefault(),
                request.sizeOrDefault(),
                Sort.by(Sort.Direction.DESC, "updatedAt")
        );

        Page<SearchDocumentEntity> page = documentRepository.search(
                tenantId,
                request.indexId(),
                request.query(),
                pageable
        );

        List<SearchResponse.SearchHit> hits = page.getContent().stream()
                .map(doc -> new SearchResponse.SearchHit(
                        doc.getId(),
                        doc.getIndexId(),
                        doc.getExternalId(),
                        doc.getTitle(),
                        computeSnippet(doc.getBodyText(), request.query()),
                        computeScore(doc, request.query()),
                        doc.getMetadataJson() != null ? parseJson(doc.getMetadataJson()) : null
                ))
                .toList();

        return new SearchResponse(hits, page.getTotalElements(), page.getNumber(), page.getSize());
    }

    private String computeSnippet(String bodyText, String query) {
        if (bodyText == null || bodyText.isBlank()) return "";
        String lower = bodyText.toLowerCase();
        String queryLower = query.toLowerCase();
        int idx = lower.indexOf(queryLower);
        if (idx < 0) {
            return bodyText.length() > 200 ? bodyText.substring(0, 200) + "..." : bodyText;
        }
        int start = Math.max(0, idx - 50);
        int end = Math.min(bodyText.length(), idx + query.length() + 150);
        String snippet = bodyText.substring(start, end);
        if (start > 0) snippet = "..." + snippet;
        if (end < bodyText.length()) snippet = snippet + "...";
        return snippet;
    }

    private double computeScore(SearchDocumentEntity doc, String query) {
        double score = 0.0;
        String queryLower = query.toLowerCase();
        if (doc.getTitle() != null && doc.getTitle().toLowerCase().contains(queryLower)) {
            score += 2.0;
        }
        if (doc.getBodyText() != null && doc.getBodyText().toLowerCase().contains(queryLower)) {
            score += 1.0;
        }
        return score;
    }

    private Object parseJson(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }
}
