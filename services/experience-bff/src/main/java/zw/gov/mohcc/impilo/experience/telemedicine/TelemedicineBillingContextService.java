package zw.gov.mohcc.impilo.experience.telemedicine;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.Map;

/**
 * Shared teleconsult billing-context enrichment (extracted from TeleconsultController so the
 * citizen virtual-care lane reuses the exact same COSTA pricing inputs).
 *
 * <p>Enriches a referral payload with {@code patient_category} (from coverage-service) and
 * {@code facility_category} (from the TUSO facility registry) so COSTA's charging rules
 * (exemptions/waivers/surcharges) can fire when the teleconsult is priced. Caller-supplied
 * values win. Best-effort: any lookup failure, or a non-numeric facility reference, leaves
 * the field unset and PCT/COSTA degrade gracefully.</p>
 */
@Service
public class TelemedicineBillingContextService {

    private static final Logger log = LoggerFactory.getLogger(TelemedicineBillingContextService.class);

    private final CoverageServiceClient coverageClient;
    private final TusoServiceClient tusoClient;

    public TelemedicineBillingContextService(CoverageServiceClient coverageClient,
                                             TusoServiceClient tusoClient) {
        this.coverageClient = coverageClient;
        this.tusoClient = tusoClient;
    }

    public void applyBillingContext(Map<String, Object> payload, String patientCpid,
                                    String facilityRef, Map<String, Object> body) {
        String patientCategory = val(body, "patientCategory", "patient_category");
        if ((patientCategory == null || patientCategory.isBlank())
                && patientCpid != null && !patientCpid.isBlank()) {
            try {
                patientCategory = nodeText(coverageClient.resolvePatientCategory(patientCpid), "category");
            } catch (Exception e) {
                log.debug("Coverage patient-category lookup failed for {}: {}", patientCpid, e.getMessage());
            }
        }
        if (patientCategory != null && !patientCategory.isBlank()) {
            payload.put("patient_category", patientCategory);
        }

        String facilityCategory = val(body, "facilityCategory", "facility_category");
        if ((facilityCategory == null || facilityCategory.isBlank())
                && facilityRef != null && facilityRef.matches("\\d+")) {
            try {
                JsonNode facility = tusoClient.getFacility(Long.parseLong(facilityRef));
                String fromRegistry = nodeText(facility, "facilityCategory");
                facilityCategory = fromRegistry != null ? fromRegistry : nodeText(facility, "level");
            } catch (Exception e) {
                log.debug("TUSO facility-category lookup failed for {}: {}", facilityRef, e.getMessage());
            }
        }
        if (facilityCategory != null && !facilityCategory.isBlank()) {
            payload.put("facility_category", facilityCategory);
        }
    }

    private String val(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null) return value.toString();
        }
        return null;
    }

    private String nodeText(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) return null;
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }
}
