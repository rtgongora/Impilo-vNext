package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.client.SurgeryServiceClient;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the surgery controller is a pure pass-through — no shape it invents, no failure it
 * swallows. Same contract as {@link ProceduresControllerTest}: a downstream exception must
 * PROPAGATE to {@code BffGlobalExceptionHandler} and become a real non-2xx, never be caught
 * here and converted to an empty 200 (the "no conditions for every patient" failure mode).
 */
class SurgeryControllerTest {

    private static final UUID EPISODE = UUID.fromString("3f9a1c2e-4b5d-4a6f-9c7e-8d0f1a2b3c4d");

    @Test
    void openEpisodeForwardsTheRawJsonBodyVerbatim() {
        String body = "{\"subjectCpid\":\"CPID-1\",\"journeyId\":\"j-1\",\"operativeIndication\":\"x\"}";
        SurgeryController controller = new SurgeryController(new StubClient() {
            @Override
            public ResponseEntity<String> openEpisode(String requestBody) {
                assertEquals(body, requestBody);
                return ResponseEntity.ok("{\"id\":\"" + EPISODE + "\",\"status\":\"ASSESSMENT\"}");
            }
        });

        var response = controller.openEpisode(body);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("{\"id\":\"" + EPISODE + "\",\"status\":\"ASSESSMENT\"}", response.getBody());
    }

    @Test
    void episodesForSubjectForwardsTheCpid() {
        SurgeryController controller = new SurgeryController(new StubClient() {
            @Override
            public ResponseEntity<String> episodesForSubject(String subjectCpid) {
                assertEquals("CPID-1", subjectCpid);
                return ResponseEntity.ok("[]");
            }
        });

        var response = controller.episodesForSubject("CPID-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("[]", response.getBody());
    }

    @Test
    void episodeDetailForwardsTheUuid() {
        SurgeryController controller = new SurgeryController(new StubClient() {
            @Override
            public ResponseEntity<String> episode(UUID id) {
                assertEquals(EPISODE, id);
                return ResponseEntity.ok("{\"id\":\"" + EPISODE + "\"}");
            }
        });

        var response = controller.episode(EPISODE);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void transitionForwardsBodyAndId() {
        SurgeryController controller = new SurgeryController(new StubClient() {
            @Override
            public ResponseEntity<String> transition(UUID id, String requestBody) {
                assertEquals(EPISODE, id);
                assertEquals("{\"status\":\"LISTED_FOR_SURGERY\"}", requestBody);
                return ResponseEntity.ok("{\"status\":\"LISTED_FOR_SURGERY\"}");
            }
        });

        var response = controller.transition(EPISODE, "{\"status\":\"LISTED_FOR_SURGERY\"}");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void assessmentPutAndGetForwardVerbatim() {
        SurgeryController controller = new SurgeryController(new StubClient() {
            @Override
            public ResponseEntity<String> recordAssessment(UUID episodeId, String requestBody) {
                assertEquals("{\"presentingProblem\":\"pain\"}", requestBody);
                return ResponseEntity.ok("{\"presentingProblem\":\"pain\"}");
            }

            @Override
            public ResponseEntity<String> assessment(UUID episodeId) {
                return ResponseEntity.ok("{\"presentingProblem\":\"pain\"}");
            }
        });

        assertEquals(HttpStatus.OK,
                controller.recordAssessment(EPISODE, "{\"presentingProblem\":\"pain\"}").getStatusCode());
        assertEquals("{\"presentingProblem\":\"pain\"}", controller.assessment(EPISODE).getBody());
    }

    @Test
    void decisionPutAndGetForwardVerbatim() {
        SurgeryController controller = new SurgeryController(new StubClient() {
            @Override
            public ResponseEntity<String> recordDecision(UUID episodeId, String requestBody) {
                assertEquals("{\"finalDecision\":\"PROCEED\"}", requestBody);
                return ResponseEntity.ok("{\"finalDecision\":\"PROCEED\"}");
            }

            @Override
            public ResponseEntity<String> decision(UUID episodeId) {
                return ResponseEntity.ok("{\"finalDecision\":\"PROCEED\"}");
            }
        });

        assertEquals(HttpStatus.OK,
                controller.recordDecision(EPISODE, "{\"finalDecision\":\"PROCEED\"}").getStatusCode());
        assertEquals("{\"finalDecision\":\"PROCEED\"}", controller.decision(EPISODE).getBody());
    }

    /**
     * The failure-does-not-render-as-empty guarantee at the boundary this controller controls:
     * a downstream exception on a READ must reach BffGlobalExceptionHandler, not be absorbed
     * into an empty list the UI would render as "this patient has no surgical episodes".
     */
    @Test
    void aDownstreamFailureOnTheListPropagatesRatherThanRenderingEmpty() {
        SurgeryController controller = new SurgeryController(new StubClient() {
            @Override
            public ResponseEntity<String> episodesForSubject(String subjectCpid) {
                throw new org.springframework.web.client.ResourceAccessException("surgery-service unreachable");
            }
        });

        assertThrows(org.springframework.web.client.ResourceAccessException.class,
                () -> controller.episodesForSubject("CPID-1"));
    }

    /** Same control on a WRITE: a failed decision write must never fake a saved decision. */
    @Test
    void aDownstreamFailureOnTheDecisionWriteAlsoPropagates() {
        SurgeryController controller = new SurgeryController(new StubClient() {
            @Override
            public ResponseEntity<String> recordDecision(UUID episodeId, String requestBody) {
                throw new org.springframework.web.client.ResourceAccessException("surgery-service unreachable");
            }
        });

        assertThrows(org.springframework.web.client.ResourceAccessException.class,
                () -> controller.recordDecision(EPISODE, "{\"finalDecision\":\"PROCEED\"}"));
    }

    /** Overridable stub so each test only implements the one method it exercises. */
    private static class StubClient extends SurgeryServiceClient {
        StubClient() {
            super(new RestTemplate(), ServiceClientConfig.testServiceEndpoints("http://surgery"));
        }
    }
}
