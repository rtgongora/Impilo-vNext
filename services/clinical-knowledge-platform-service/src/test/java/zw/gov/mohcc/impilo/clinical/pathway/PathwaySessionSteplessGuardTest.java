package zw.gov.mohcc.impilo.clinical.pathway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.clinical.events.ClinicalOutboxWriter;
import zw.gov.mohcc.impilo.clinical.persistence.entity.PathwayDefinitionEntity;
import zw.gov.mohcc.impilo.clinical.persistence.entity.PathwaySessionEntity;
import zw.gov.mohcc.impilo.clinical.persistence.entity.PathwayStepEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.PathwayDefinitionRepository;
import zw.gov.mohcc.impilo.clinical.persistence.repository.PathwaySessionRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A pathway with no steps is not a pathway with nothing left to do.
 *
 * <p>Eight of the eleven seeded {@code ED_*} definitions had zero steps, and the session service
 * treated a stepless definition as startable: it created an ACTIVE session at step 1 and saved it,
 * while the step snapshot returned an empty map. So selecting "ED — Ectopic pregnancy" on a shocked
 * woman rendered a blank screen and wrote a record saying an ectopic pathway had been opened —
 * a record of care that did not happen, indistinguishable from a pathway already worked through.
 * A single {@code advance} then marked it COMPLETED and published PATHWAY_SESSION_COMPLETED to the
 * estate.
 *
 * <p>This path shipped with no test at all, which is how it reached production. These are that test.
 */
class PathwaySessionSteplessGuardTest {

    private final PathwayDefinitionRepository definitionRepo = mock(PathwayDefinitionRepository.class);
    private final PathwaySessionRepository sessionRepo = mock(PathwaySessionRepository.class);
    private final ClinicalOutboxWriter outbox = mock(ClinicalOutboxWriter.class);

    private final PathwaySessionService service = new PathwaySessionService(
            definitionRepo, sessionRepo, new ObjectMapper(), outbox);

    @Test
    @DisplayName("starting a stepless pathway is refused, and no session is saved")
    void steplessPathwayCannotBeStarted() {
        UUID id = UUID.randomUUID();
        when(definitionRepo.findByIdWithSteps(id)).thenReturn(Optional.of(definition(id, "ED_ECTOPIC", List.of())));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.start("t", "a", id, "p", "e"));

        assertEquals(422, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("ED_ECTOPIC"));
        assertTrue(ex.getReason().contains("do not record this pathway as followed"),
                "the message must tell the clinician what to do, not just that it failed");
        // The containment: nothing is written, so there is no false record to later mistake for care.
        verify(sessionRepo, never()).save(any());
    }

    @Test
    @DisplayName("a pathway with steps still starts")
    void pathwayWithStepsStarts() {
        UUID id = UUID.randomUUID();
        when(definitionRepo.findByIdWithSteps(id))
                .thenReturn(Optional.of(definition(id, "ED_SEPSIS", List.of(step(id, 1)))));
        when(sessionRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        PathwaySessionEntity s = service.start("t", "a", id, "p", "e");

        assertEquals("ACTIVE", s.getStatus());
        assertEquals(1, s.getCurrentStepOrder());
    }

    @Test
    @DisplayName("the step snapshot distinguishes 'no steps' from 'a step with no content'")
    void snapshotDistinguishesAbsentFromEmpty() {
        UUID id = UUID.randomUUID();
        // A definition that somehow has no steps but a live session (a pre-guard session).
        when(definitionRepo.findByIdWithSteps(id)).thenReturn(Optional.of(definition(id, "ED_ACS", List.of())));
        PathwaySessionEntity s = session(id, 1);

        Map<String, Object> snap = service.currentStepSnapshot(s);

        // The old behaviour returned {} here, which a UI renders identically to a real empty step.
        assertEquals(Boolean.TRUE, snap.get("current_step_absent"));
        assertEquals("PATHWAY_HAS_NO_STEPS", snap.get("reason"));
    }

    @Test
    @DisplayName("advancing a stepless session cannot forge a completion, and publishes nothing")
    void advancingASteplessSessionDoesNotCompleteOrPublish() {
        UUID id = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        PathwaySessionEntity s = session(id, 1);
        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(s));
        when(definitionRepo.findByIdWithSteps(id)).thenReturn(Optional.of(definition(id, "ED_STROKE", List.of())));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.advance(sessionId, Map.of()));

        assertEquals(422, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("not completing it"));
        // The part that matters most: no false completion reaches the rest of the estate.
        verify(outbox, never()).enqueue(anyString(), any(), any(), any(), any());
    }

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────
    //
    // Real entities, populated by reflection on the private fields, rather than Mockito stubs — the
    // getters are final and cannot be stubbed, and a real entity is the more honest fixture anyway:
    // the test then exercises the actual getSteps()/getConditionCode() the service calls.

    private static PathwayDefinitionEntity definition(UUID id, String code, List<PathwayStepEntity> steps) {
        PathwayDefinitionEntity def = new PathwayDefinitionEntity();
        set(def, "id", id);
        set(def, "conditionCode", code);
        set(def, "steps", new java.util.ArrayList<>(steps));
        return def;
    }

    private static PathwayStepEntity step(UUID pathwayId, int order) {
        PathwayStepEntity st = new PathwayStepEntity();
        set(st, "stepOrder", order);
        set(st, "prompt", "prompt " + order);
        set(st, "stepType", "ASSESSMENT");
        return st;
    }

    private static void set(Object target, String field, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("fixture could not set " + field, e);
        }
    }

    private static PathwaySessionEntity session(UUID pathwayId, int currentStep) {
        PathwaySessionEntity s = new PathwaySessionEntity();
        s.setPathwayId(pathwayId);
        s.setTenantId("t");
        s.setActorId("a");
        s.setStateJson(new ObjectMapper().createObjectNode());
        s.setCurrentStepOrder(currentStep);
        s.setStatus("ACTIVE");
        return s;
    }
}
