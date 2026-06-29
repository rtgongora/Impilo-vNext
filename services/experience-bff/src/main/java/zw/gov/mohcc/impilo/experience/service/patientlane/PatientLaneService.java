package zw.gov.mohcc.impilo.experience.service.patientlane;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.service.patientlane.PatientMessageCatalog.PatientStatusMessage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Patient lane composition (SYS-3 / G-CT-01). Composes a citizen-facing visit or inpatient status
 * from the sovereign services (PCT, inpatient) and translates the canonical lifecycle state into a
 * plain-language message via {@link PatientMessageCatalog}. The BFF holds no state — this is pure
 * read-composition (its role per doctrine: orchestration, not source of truth).
 *
 * <p>Degrades honestly: if a downstream is unavailable or the id is unknown, the status is marked
 * {@code available: false} with a safe message rather than failing the whole patient view.
 */
@Service
public class PatientLaneService {

    private static final Logger log = LoggerFactory.getLogger(PatientLaneService.class);

    private final PctServiceClient pctClient;
    private final InpatientServiceClient inpatientClient;

    public PatientLaneService(PctServiceClient pctClient, InpatientServiceClient inpatientClient) {
        this.pctClient = pctClient;
        this.inpatientClient = inpatientClient;
    }

    /** Composes the patient-facing status of an outpatient visit/journey by its transaction id. */
    public Map<String, Object> visitStatus(String transactionId) {
        JsonNode journey;
        try {
            journey = pctClient.getJourney(transactionId);
        } catch (Exception e) {
            log.warn("Patient lane: PCT journey {} unavailable: {}", transactionId, e.toString());
            return unavailable(transactionId, "visit");
        }
        if (journey == null || journey.isNull() || !journey.hasNonNull("state")) {
            return unavailable(transactionId, "visit");
        }
        String state = text(journey, "state");
        PatientStatusMessage msg = PatientMessageCatalog.forJourneyState(state);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("transactionId", transactionId);
        out.put("kind", "visit");
        out.put("available", true);
        out.put("state", state);
        out.put("stage", msg.stage());
        out.put("statusLabel", msg.label());
        out.put("message", msg.message());
        out.put("messageKey", msg.messageKey());
        out.put("facilityId", text(journey, "facilityId"));
        out.put("updatedAt", text(journey, "updatedAt"));
        return out;
    }

    /** Composes the patient-facing status of an inpatient admission by its admission ref. */
    public Map<String, Object> inpatientStatus(String admissionRef) {
        JsonNode admission;
        try {
            admission = inpatientClient.getAdmission(admissionRef);
        } catch (Exception e) {
            log.warn("Patient lane: inpatient admission {} unavailable: {}", admissionRef, e.toString());
            return unavailable(admissionRef, "inpatient");
        }
        if (admission == null || admission.isNull() || !admission.hasNonNull("status")) {
            return unavailable(admissionRef, "inpatient");
        }
        String status = text(admission, "status");
        boolean active = !"DISCHARGED".equalsIgnoreCase(status) && !"CANCELLED".equalsIgnoreCase(status);
        PatientStatusMessage msg = PatientMessageCatalog.forInpatient(active);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("admissionRef", admissionRef);
        out.put("kind", "inpatient");
        out.put("available", true);
        out.put("status", status);
        out.put("stage", msg.stage());
        out.put("statusLabel", msg.label());
        out.put("message", msg.message());
        out.put("messageKey", msg.messageKey());
        // Ward/bed are operational, non-clinical locators the patient may see (best-effort).
        putIfPresent(out, "ward", admission, "wardName", "ward");
        putIfPresent(out, "bed", admission, "bedLabel", "bed");
        return out;
    }

    private Map<String, Object> unavailable(String id, String kind) {
        PatientStatusMessage msg = PatientMessageCatalog.forJourneyState(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(kind.equals("inpatient") ? "admissionRef" : "transactionId", id);
        out.put("kind", kind);
        out.put("available", false);
        out.put("stage", msg.stage());
        out.put("statusLabel", msg.label());
        out.put("message", msg.message());
        out.put("messageKey", msg.messageKey());
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** Copies the first present field (trying several source names) into the output. */
    private static void putIfPresent(Map<String, Object> out, String outKey, JsonNode node, String... candidates) {
        for (String c : candidates) {
            if (node.hasNonNull(c)) {
                out.put(outKey, node.get(c).asText());
                return;
            }
        }
    }
}
