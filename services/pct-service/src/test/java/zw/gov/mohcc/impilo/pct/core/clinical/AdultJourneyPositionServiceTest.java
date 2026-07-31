package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdultJourneyPositionServiceTest {

    @Test
    void invalidStepKeyIsRejected() {
        AdultJourneyPositionService service = new AdultJourneyPositionService(null, null);
        Map<String, Object> body = baseBody();
        body.put("step_key", "NOT_A_STEP");
        body.put("step_index", 1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.record(body));
        assertTrue(ex.getMessage().contains("Unrecognised step_key"));
    }

    @Test
    void stepIndexMustMatchStepKey() {
        AdultJourneyPositionService service = new AdultJourneyPositionService(null, null);
        Map<String, Object> body = baseBody();
        body.put("step_key", "CLERKING");
        body.put("step_index", 5);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.record(body));
        assertTrue(ex.getMessage().contains("does not match step_key"));
    }

    @Test
    void missingStepIndexIsRejected() {
        AdultJourneyPositionService service = new AdultJourneyPositionService(null, null);
        Map<String, Object> body = baseBody();
        body.put("step_key", "PRESENTING");
        body.remove("step_index");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.record(body));
        assertTrue(ex.getMessage().contains("step_index"));
    }

    @Test
    void missingAnchorIsRejected() {
        AdultJourneyPositionService service = new AdultJourneyPositionService(null, null);
        Map<String, Object> body = baseBody();
        body.put("step_key", "PRESENTING");
        body.put("step_index", 1);
        body.remove("encounter_id");
        body.remove("journey_id");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.record(body));
        assertTrue(ex.getMessage().contains("journey_id or encounter_id"));
    }

    @Test
    void missingSubjectCpidIsRejected() {
        AdultJourneyPositionService service = new AdultJourneyPositionService(null, null);
        Map<String, Object> body = baseBody();
        body.put("step_key", "PRESENTING");
        body.put("step_index", 1);
        body.remove("subject_cpid");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.record(body));
        assertTrue(ex.getMessage().contains("subject_cpid"));
    }

    private static Map<String, Object> baseBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("subject_cpid", "CPID-1");
        body.put("encounter_id", "enc-1");
        return body;
    }
}
