package zw.gov.mohcc.impilo.experience.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maps mobile/BFF admission payloads to sovereign inpatient-service fields.
 */
public final class InpatientAdmissionNormalizer {

    private static final UUID DEFAULT_TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID DEFAULT_FACILITY = UUID.fromString("f1000000-0000-0000-0000-000000000001");

    private InpatientAdmissionNormalizer() {}

    public static Map<String, Object> normalize(String tenantId, Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tenantId", tenantId != null ? tenantId : DEFAULT_TENANT.toString());
        out.put("subjectCpid", first(body, "subjectCpid", "subject_cpid", "patientId", "patient_id"));
        out.put("encounterId", first(body, "encounterId", "encounter_id", "encounterId")
                != null ? first(body, "encounterId", "encounter_id") : UUID.randomUUID().toString());
        out.put("facilityId", first(body, "facilityId", "facility_id") != null
                ? first(body, "facilityId", "facility_id") : DEFAULT_FACILITY.toString());
        putIfPresent(out, "wardId", body, "wardId", "ward_id");
        putIfPresent(out, "bedId", body, "bedId", "bed_id");
        putIfPresent(out, "admittingDiagnosis", body, "admittingDiagnosis", "admitting_diagnosis");
        putIfPresent(out, "admissionType", body, "admissionType", "admission_type");
        putIfPresent(out, "dietOrders", body, "dietOrders", "diet_orders");
        putIfPresent(out, "activityLevel", body, "activityLevel", "activity_level");
        return out;
    }

    private static String first(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String key : keys) {
            Object v = body.get(key);
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return null;
    }

    private static void putIfPresent(Map<String, Object> out, String target, Map<String, Object> body, String... keys) {
        String v = first(body, keys);
        if (v != null) out.put(target, v);
    }
}
