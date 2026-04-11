package zw.gov.mohcc.impilo.clinical.curation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.clinical.events.ClinicalOutboxWriter;
import zw.gov.mohcc.impilo.clinical.persistence.entity.ClinicalKnowledgeItemEntity;
import zw.gov.mohcc.impilo.clinical.persistence.entity.KnowledgeReviewItemEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.ClinicalKnowledgeItemRepository;
import zw.gov.mohcc.impilo.clinical.persistence.repository.KnowledgeReviewItemRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgeCurationService {

    private final KnowledgeReviewItemRepository reviewItemRepository;
    private final ClinicalKnowledgeItemRepository knowledgeItemRepository;
    private final ClinicalOutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    public KnowledgeCurationService(
            KnowledgeReviewItemRepository reviewItemRepository,
            ClinicalKnowledgeItemRepository knowledgeItemRepository,
            ClinicalOutboxWriter outboxWriter,
            ObjectMapper objectMapper) {
        this.reviewItemRepository = reviewItemRepository;
        this.knowledgeItemRepository = knowledgeItemRepository;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listReviewItems(String status) {
        String s = status == null || status.isBlank() ? "PROPOSED" : status.trim();
        List<Map<String, Object>> out = new ArrayList<>();
        for (KnowledgeReviewItemEntity e : reviewItemRepository.findByReviewStatusOrderByCreatedAtAsc(s)) {
            out.add(toReviewMap(e));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> decide(UUID reviewItemId, String decision, String reviewer, String notes) {
        KnowledgeReviewItemEntity row = reviewItemRepository.findById(reviewItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "review item not found"));
        if (!"PROPOSED".equals(row.getReviewStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "review item already decided");
        }
        String rev = reviewer != null && !reviewer.isBlank() ? reviewer : "unknown_reviewer";
        Instant now = Instant.now();
        row.setReviewerNotes(notes);
        row.setDecidedAt(now);

        if ("APPROVED".equalsIgnoreCase(decision)) {
            row.setReviewStatus("APPROVED");
            reviewItemRepository.save(row);
            ClinicalKnowledgeItemEntity item = materializeFromProposal(row.getProposedPayload(), rev);
            knowledgeItemRepository.save(item);
            outboxWriter.enqueue(
                    "KNOWLEDGE_ITEM_APPROVED",
                    "default",
                    "ClinicalKnowledgeItem",
                    item.getId().toString(),
                    Map.of(
                            "knowledge_item_id", item.getId().toString(),
                            "type", item.getType(),
                            "review_item_id", reviewItemId.toString()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("review_item_id", reviewItemId.toString());
            m.put("knowledge_item_id", item.getId().toString());
            m.put("status", "APPROVED");
            return m;
        }
        if ("REJECTED".equalsIgnoreCase(decision)) {
            row.setReviewStatus("REJECTED");
            reviewItemRepository.save(row);
            return Map.of("review_item_id", reviewItemId.toString(), "status", "REJECTED");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision must be APPROVED or REJECTED");
    }

    private ClinicalKnowledgeItemEntity materializeFromProposal(JsonNode proposed, String approvedBy) {
        String rawType = proposed.path("type").asText("DOCUMENT_EXCERPT");
        String type = "SOURCE_EXCERPT".equals(rawType) ? "DOCUMENT_EXCERPT" : rawType;
        String title = proposed.path("title").asText("Curated excerpt");
        String excerpt = proposed.has("excerpt")
                ? proposed.get("excerpt").asText("")
                : proposed.path("summary").asText("");
        String documentId = proposed.path("document_id").asText(null);
        String sectionPath = proposed.path("section_path").asText(null);

        ClinicalKnowledgeItemEntity e = new ClinicalKnowledgeItemEntity();
        e.setType(type);
        e.setCanonicalCode(sectionPath != null ? sectionPath.replace('/', '_') : null);
        e.setTitle(title);
        e.setSummary(excerpt.length() > 4000 ? excerpt.substring(0, 4000) : excerpt);
        e.setPopulation("national");
        JsonNode elig = proposed.path("eligibility_json");
        e.setEligibilityJson(elig.isObject() ? elig : objectMapper.createObjectNode());
        e.setLogicJson(objectMapper.createObjectNode());
        e.setEvidenceJson(objectMapper.valueToTree(List.of(Map.of("text", excerpt))));
        List<Map<String, String>> refs = new ArrayList<>();
        if (documentId != null) {
            refs.add(Map.of("document_id", documentId));
        }
        if (sectionPath != null) {
            refs.add(Map.of("section_path", sectionPath));
        }
        e.setSourceRefsJson(objectMapper.valueToTree(refs));
        e.setEffectiveStart(LocalDate.now());
        e.setApprovalStatus("APPROVED");
        e.setVersion(1);
        e.setApprovedBy(approvedBy);
        e.setCreatedBy(approvedBy);
        e.setUpdatedAt(Instant.now());
        return e;
    }

    private static Map<String, Object> toReviewMap(KnowledgeReviewItemEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("extraction_job_id", e.getExtractionJobId() != null ? e.getExtractionJobId().toString() : null);
        m.put("review_status", e.getReviewStatus());
        m.put("proposed_payload", e.getProposedPayload());
        m.put("created_at", e.getCreatedAt().toString());
        return m;
    }
}
