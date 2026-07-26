package zw.gov.mohcc.impilo.clinical.pathway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.clinical.events.ClinicalOutboxWriter;
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
    private final ClinicalOutboxWriter clinicalOutboxWriter;

    public PathwaySessionService(
            PathwayDefinitionRepository pathwayDefinitionRepository,
            PathwaySessionRepository sessionRepository,
            ObjectMapper objectMapper,
            ClinicalOutboxWriter clinicalOutboxWriter) {
        this.pathwayDefinitionRepository = pathwayDefinitionRepository;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
        this.clinicalOutboxWriter = clinicalOutboxWriter;
    }

    /**
     * Open a pathway session.
     *
     * <p><b>A definition with no steps is not startable.</b> Before this guard existed, selecting
     * "ED — Ectopic pregnancy" on a shocked woman created an ACTIVE session at step 1 and saved it,
     * while {@code currentStepSnapshot} returned an empty map — so the screen showed nothing and the
     * record said an ectopic pathway had been opened. That is not a missing feature; it is a record
     * of care that did not happen, and it is indistinguishable from a pathway with nothing left to
     * do. Eight of the eleven seeded {@code ED_*} definitions were in that state.
     *
     * <p>Refusing is the containment: an unstartable pathway becomes a visible error at the point of
     * selection instead of a silent false record, and the clinician goes and finds the protocol.
     */
    @Transactional
    public PathwaySessionEntity start(String tenantId, String actorId, UUID pathwayId, String patientId, String encounterId) {
        PathwayDefinitionEntity def = pathwayDefinitionRepository.findByIdWithSteps(pathwayId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pathway not found"));
        if (def.getSteps() == null || def.getSteps().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "pathway '" + def.getConditionCode() + "' has no steps and cannot be started. "
                            + "It is defined but not yet implemented — use the unit's written protocol "
                            + "and do not record this pathway as followed.");
        }
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

    /**
     * Snapshot of the session's current pathway step (prompt + capture schema) for UI start/advance flows.
     */
    public Map<String, Object> currentStepSnapshot(PathwaySessionEntity s) {
        PathwayDefinitionEntity def = pathwayDefinitionRepository.findByIdWithSteps(s.getPathwayId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "pathway not found"));
        return def.getSteps().stream()
                .filter(st -> Objects.equals(st.getStepOrder(), s.getCurrentStepOrder()))
                .findFirst()
                .map(st -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("current_prompt", st.getPrompt());
                    m.put("current_step_type", st.getStepType());
                    m.put("data_capture_schema", st.getDataCaptureSchema());
                    return m;
                })
                .orElseGet(() -> {
                    // "There is no step at this order" and "there is a step and it is empty" are
                    // different facts, and an empty map said both. A caller that cannot tell them
                    // apart renders a blank panel either way — which is how a pathway with no
                    // content passed for a pathway with nothing left to do.
                    Map<String, Object> absent = new LinkedHashMap<>();
                    absent.put("current_step_absent", true);
                    absent.put("current_step_order", s.getCurrentStepOrder());
                    absent.put("reason", def.getSteps() == null || def.getSteps().isEmpty()
                            ? "PATHWAY_HAS_NO_STEPS"
                            : "NO_STEP_AT_THIS_ORDER");
                    return absent;
                });
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
        } else if (steps.isEmpty()) {
            // Defence in depth for sessions that predate the start() guard. Reaching the end of a
            // pathway that HAD steps is a completion; reaching the end of one that never had any is
            // not — and the old code could not tell the difference, so a single advance marked the
            // session COMPLETED, stamped completedAt, and published PATHWAY_SESSION_COMPLETED to the
            // estate. A false completion broadcast to other services is worse than a local one,
            // because every consumer and every indicator then counts it.
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "pathway has no steps: this session cannot be advanced or completed. "
                            + "Reaching the end of an empty pathway is not completing it.");
        } else {
            s.setStatus("COMPLETED");
            s.setCompletedAt(Instant.now());
        }
        s.setStateJson(state);
        sessionRepository.save(s);
        if ("COMPLETED".equals(s.getStatus())) {
            clinicalOutboxWriter.enqueue(
                    "PATHWAY_SESSION_COMPLETED",
                    s.getTenantId(),
                    "PathwaySession",
                    s.getId().toString(),
                    Map.of(
                            "session_id", s.getId().toString(),
                            "pathway_id", s.getPathwayId().toString()));
        }

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
