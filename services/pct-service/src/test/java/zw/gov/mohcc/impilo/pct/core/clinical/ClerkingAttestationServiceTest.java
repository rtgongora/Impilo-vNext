package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClerkingAttestationServiceTest {

    @Test
    void omittingStanceDoesNotInventConfirmation() {
        ClerkingAttestationService service = new ClerkingAttestationService(null, null);
        Map<String, Object> body = new HashMap<>();
        body.put("subject_cpid", "CPID");
        body.put("encounter_id", "enc-1");
        body.put("section_key", "PROBLEMS");
        // no stance

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.attest(body));
        assertTrue(ex.getMessage().contains("stance is required"));
        assertTrue(ex.getMessage().toLowerCase().contains("silence")
                || ex.getMessage().toLowerCase().contains("confirmation"));
    }
}
