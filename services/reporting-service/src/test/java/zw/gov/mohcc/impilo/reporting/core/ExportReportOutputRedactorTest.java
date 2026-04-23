package zw.gov.mohcc.impilo.reporting.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportReportOutputRedactorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void redactsSensitiveCsvColumns() {
        VisibilityProfile profile = new VisibilityProfile(
                "DEIDENTIFIED_ROW_LEVEL", "MASKED", "NONE", false, "DEIDENTIFIED_PERSON_LEVEL",
                null, null, null, null, "REDACTED", false);
        String csv = "id,patient_phone,region\n1,+263771,Harare\n";
        String out = ExportReportOutputRedactor.apply(csv, ExportFormat.CSV, profile, mapper);
        assertTrue(out.contains("***"));
        assertFalse(out.contains("+263771"));
    }

    @Test
    void shapesJsonWithVisibilityProfile() throws Exception {
        VisibilityProfile profile = new VisibilityProfile(
                "DEIDENTIFIED_ROW_LEVEL", "MASKED", "NONE", false, "DEIDENTIFIED_PERSON_LEVEL",
                null, null, null, null, "REDACTED", false);
        String json = "{\"rows\":[{\"id\":1,\"phone\":\"123\",\"region\":\"A\"}]}";
        String out = ExportReportOutputRedactor.apply(json, ExportFormat.JSON, profile, mapper);
        JsonNode tree = mapper.readTree(out);
        assertTrue(tree.at("/rows/0/phone").asText().contains("*"));
    }
}
