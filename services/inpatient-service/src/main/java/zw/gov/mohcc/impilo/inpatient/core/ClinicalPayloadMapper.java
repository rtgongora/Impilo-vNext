package zw.gov.mohcc.impilo.inpatient.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builds snake_case API maps for mobile and BFF consumers.
 */
public final class ClinicalPayloadMapper {

    private ClinicalPayloadMapper() {}

    public static String str(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String key : keys) {
            Object v = body.get(key);
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return null;
    }

    public static UUID uuid(Map<String, Object> body, String... keys) {
        String raw = str(body, keys);
        if (raw == null) return null;
        return UUID.fromString(raw);
    }

    public static Integer integer(Map<String, Object> body, String... keys) {
        String raw = str(body, keys);
        if (raw == null) return null;
        return Integer.parseInt(raw);
    }

    public static Map<String, Object> admissionMap(
            UUID admissionRef, String subjectCpid, String status,
            String admittingDiagnosis, String admissionType, String dietOrders, String activityLevel,
            UUID wardId, UUID bedId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", admissionRef != null ? admissionRef.toString() : null);
        m.put("admission_ref", admissionRef != null ? admissionRef.toString() : null);
        m.put("subject_cpid", subjectCpid);
        m.put("patient_id", subjectCpid);
        m.put("status", status);
        m.put("admitting_diagnosis", admittingDiagnosis);
        m.put("admission_type", admissionType);
        m.put("diet_orders", dietOrders);
        m.put("activity_level", activityLevel);
        m.put("ward_id", wardId != null ? wardId.toString() : null);
        m.put("bed_id", bedId != null ? bedId.toString() : null);
        return m;
    }
}
