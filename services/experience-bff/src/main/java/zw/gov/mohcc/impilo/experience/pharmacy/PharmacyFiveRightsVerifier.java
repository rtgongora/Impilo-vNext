package zw.gov.mohcc.impilo.experience.pharmacy;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Evaluates the pharmacy "five rights" against a sovereign prescription resource.
 */
public final class PharmacyFiveRightsVerifier {

    private PharmacyFiveRightsVerifier() {}

    public record Result(boolean verified, Map<String, Boolean> rights) {}

    public static Result evaluate(JsonNode prescription, String expectedPatientId) {
        JsonNode attrs = prescription != null && prescription.has("attributes")
                ? prescription.get("attributes")
                : prescription;

        if (attrs == null || attrs.isMissingNode() || attrs.isNull()) {
            Map<String, Boolean> rights = emptyRights(false);
            return new Result(false, rights);
        }

        String status = text(attrs, "status");
        boolean dispenseable = "ACTIVE".equalsIgnoreCase(status) || "PENDING".equalsIgnoreCase(status);

        boolean rightPatient = expectedPatientId == null
                || expectedPatientId.isBlank()
                || expectedPatientId.equals(text(attrs, "patient_id"));
        boolean rightDrug = !text(attrs, "medication_name").isBlank();
        boolean rightDose = !text(attrs, "dosage").isBlank();
        String route = text(attrs, "route");
        boolean rightRoute = !route.isBlank();
        boolean rightTime = !text(attrs, "frequency").isBlank();

        Map<String, Boolean> rights = new LinkedHashMap<>();
        rights.put("rightPatient", rightPatient);
        rights.put("rightDrug", rightDrug);
        rights.put("rightDose", rightDose);
        rights.put("rightRoute", rightRoute);
        rights.put("rightTime", rightTime);

        boolean verified = dispenseable && rights.values().stream().allMatch(Boolean::booleanValue);
        return new Result(verified, rights);
    }

    private static Map<String, Boolean> emptyRights(boolean value) {
        Map<String, Boolean> rights = new LinkedHashMap<>();
        rights.put("rightPatient", value);
        rights.put("rightDrug", value);
        rights.put("rightDose", value);
        rights.put("rightRoute", value);
        rights.put("rightTime", value);
        return rights;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        String value = node.path(field).asText("");
        return value != null ? value.trim() : "";
    }
}
