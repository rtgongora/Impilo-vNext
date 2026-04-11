package zw.gov.mohcc.impilo.clinical.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.clinical.persistence.entity.RecommendationTraceEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.RecommendationTraceRepository;

import java.util.List;
import java.util.Map;

@Service
public class TraceService {

    private final RecommendationTraceRepository traceRepository;
    private final ObjectMapper objectMapper;

    public TraceService(RecommendationTraceRepository traceRepository, ObjectMapper objectMapper) {
        this.traceRepository = traceRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RecommendationTraceEntity record(
            String userId,
            String role,
            String patientId,
            String encounterId,
            String requestType,
            Map<String, Object> inputContext,
            Map<String, Object> recommendations,
            List<Map<String, Object>> citations,
            List<Map<String, Object>> rulesTriggered,
            String knowledgeVersion,
            String sourceVersion,
            String supportMode) {

        RecommendationTraceEntity e = new RecommendationTraceEntity();
        e.setUserId(userId);
        e.setRole(role);
        e.setPatientId(patientId);
        e.setEncounterId(encounterId);
        e.setRequestType(requestType);
        e.setInputContextJson(objectMapper.valueToTree(inputContext == null ? Map.of() : inputContext));
        e.setRecommendationsJson(objectMapper.valueToTree(recommendations == null ? Map.of() : recommendations));
        e.setSourceRefsJson(objectMapper.valueToTree(Map.of("citations", citations == null ? List.of() : citations)));
        e.setRulesTriggeredJson(objectMapper.valueToTree(Map.of("alerts", rulesTriggered == null ? List.of() : rulesTriggered)));
        e.setKnowledgeVersion(knowledgeVersion);
        e.setSourceVersion(sourceVersion);
        e.setSupportMode(supportMode);
        return traceRepository.save(e);
    }

    public JsonNode findTraceJson(java.util.UUID id) {
        return traceRepository.findById(id)
                .map(e -> {
                    var n = objectMapper.createObjectNode();
                    n.put("id", e.getId().toString());
                    n.put("user_id", e.getUserId());
                    n.put("role", e.getRole());
                    n.put("request_type", e.getRequestType());
                    n.put("support_mode", e.getSupportMode());
                    n.set("input_context_json", e.getInputContextJson());
                    n.set("recommendations_json", e.getRecommendationsJson());
                    return n;
                })
                .orElse(null);
    }
}
