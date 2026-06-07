package zw.gov.mohcc.impilo.experience.pharmacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PharmacyFiveRightsVerifierTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void passesWhenPrescriptionHasRequiredFields() throws Exception {
        var rx = mapper.readTree("""
                {"id":"rx-1","type":"prescription","attributes":{
                  "patient_id":"cpid-1","medication_name":"Amoxicillin","dosage":"500mg",
                  "route":"PO","frequency":"TDS","status":"ACTIVE"
                }}
                """);

        PharmacyFiveRightsVerifier.Result result =
                PharmacyFiveRightsVerifier.evaluate(rx, "cpid-1");

        assertTrue(result.verified());
        assertTrue(result.rights().get("rightDrug"));
    }

    @Test
    void failsWhenPatientMismatch() throws Exception {
        var rx = mapper.readTree("""
                {"attributes":{"patient_id":"cpid-1","medication_name":"A","dosage":"1","route":"PO","frequency":"OD","status":"ACTIVE"}}
                """);

        PharmacyFiveRightsVerifier.Result result =
                PharmacyFiveRightsVerifier.evaluate(rx, "cpid-2");

        assertFalse(result.verified());
        assertFalse(result.rights().get("rightPatient"));
    }
}
