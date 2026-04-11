package zw.gov.mohcc.impilo.clinical.pathway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.clinical.persistence.entity.PathwayDefinitionEntity;
import zw.gov.mohcc.impilo.clinical.persistence.entity.PathwaySessionEntity;
import zw.gov.mohcc.impilo.clinical.persistence.entity.PathwayStepEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.PathwayDefinitionRepository;
import zw.gov.mohcc.impilo.clinical.persistence.repository.PathwaySessionRepository;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PathwaySessionService {

    private final PathwayDefinitionRepository pathwayDefinitionRepository;
    private final PathwaySessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    public PathwaySessionService(
            PathwayDefinitionRepository pathwayDefinitionRepository,
            PathwaySessionRepository sessionRepository,
            ObjectMapper objectMapper) {
        this.pathwayDefinitionRepository = pathwayDefinitionRepository;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PathwaySessionEntity start(String tenantId, String actorId, UUID pathwayId, String patientId, String encounterId) {
        pathwayDefinitionRepository.findByIdWithSteps(pathwayId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pathway not found"));
        PathwaySessionEntity s = new PathwaySessionEntity();
        s.setPathwayId(pathwayId);
        s.setTenantId(tenantId);
        s.setActorId(actorId);
        s.setPatientId(patientId);
        s.setEncounterId(encounterId);
        s.setStateJson(objectMapper.createObjectNode());
        s.setCurrentStepOrder(1);
        s.setStatus("ACTIVE");
        return sessionRepository.save(s);
    }

    @Transactional
    public Map<String, Object> advance(UUID sessionId, Map<String, Object> stepAnswers) {
        PathwaySessionEntity s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found"));
        PathwayDefinitionEntity def = pathwayDefinitionRepository.findByIdWithSteps(s.getPathwayId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pathway not found"));
        ObjectNode state = (ObjectNode) s.getStateJson();
        state.set("step_" + s.getCurrentStepOrder(), objectMapper.valueToTree(stepAnswers == null ? Map.of() : stepAnswers));

        List<PathwayStepEntity> steps = def.getSteps().stream()
                .sorted(Comparator.comparing(PathwayStepEntity::getStepOrder))
                .collect(Collectors.toList());
        Optional<PathwayStepEntity> next = steps.stream()
                .filter(st -> st.getStepOrder() > s.getCurrentStepOrder())
                .findFirst();
        if (next.isPresent()) {
            s.setCurrentStepOrder(next.get().getStepOrder());
        } else {
            s.setStatus("COMPLETED");
            s.setCompletedAt(Instant.now());
        }
        s.setStateJson(state);
        sessionRepository.save(s);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("session_id", s.getId().toString());
        out.put("status", s.getStatus());
        out.put("current_step_order", s.getCurrentStepOrder());
        if (next.isPresent()) {
            PathwayStepEntity st = next.get();
            out.put("current_prompt", st.getPrompt());
            out.put("current_step_type", st.getStepType());
            out.put("data_capture_schema", st.getDataCaptureSchema());
        } else {
            out.put("summary", "Pathway complete — capture escalation and documentation per local protocol.");
        }
        return out;
    }

    public List<Map<String, Object>> listPathways() {
        List<PathwayDefinitionEntity> defs = pathwayDefinitionRepository.findAllActiveWithSteps("ACTIVE");
        defs.sort(Comparator.comparing(PathwayDefinitionEntity::getPathwayName));
        List<Map<String, Object>> out = new ArrayList<>();
        for (PathwayDefinitionEntity p : defs) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId().toString());
            m.put("pathway_name", p.getPathwayName());
            m.put("condition_code", p.getConditionCode());
            m.put("version", p.getVersion());
            m.put("step_count", p.getSteps().size());
            out.add(m);
        }
        return out;
    }

    public Map<String, Object> getPathwayDetail(UUID id) {
        PathwayDefinitionEntity p = pathwayDefinitionRepository.findByIdWithSteps(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pathway not found"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId().toString());
        m.put("pathway_name", p.getPathwayName());
        m.put("condition_code", p.getConditionCode());
        m.put("steps", p.getSteps().stream().sorted(Comparator.comparing(PathwayStepEntity::getStepOrder)).map(st -> {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("order", st.getStepOrder());
            sm.put("type", st.getStepType());
            sm.put("prompt", st.getPrompt());
            sm.put("data_capture_schema", st.getDataCaptureSchema());
            return sm;
        }).toList());
        return m;
    }
}
