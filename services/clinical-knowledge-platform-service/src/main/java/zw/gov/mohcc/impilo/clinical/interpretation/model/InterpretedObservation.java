package zw.gov.mohcc.impilo.clinical.interpretation.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The result of interpreting one {@link ObservationInput}: its classification, FHIR interpretation code,
 * the reference range used (with provenance), and delta/trend versus a prior value.
 *
 * @param loincCode    echoed analyte identity (nullable)
 * @param localCode    echoed analyte identity (nullable)
 * @param displayName  echoed display name
 * @param category     LAB | VITAL | DERIVED
 * @param value        the interpreted value
 * @param unit         the value's unit
 * @param code         the interpretation classification
 * @param range        the reference range used (null for NO_REFERENCE_RANGE / UNIT_MISMATCH / DATA_QUALITY)
 * @param delta        value minus prior (in the value's unit) — null when no comparable prior
 * @param trend        direction of change — null when no comparable prior
 * @param note         short human-readable explanation
 */
public record InterpretedObservation(
        String loincCode,
        String localCode,
        String displayName,
        String category,
        BigDecimal value,
        String unit,
        InterpretationCode code,
        ReferenceRange range,
        BigDecimal delta,
        Trend trend,
        String note
) {

    /** Advisory: the interpretation never overrides clinician judgement. Always true. */
    public boolean advisory() {
        return true;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("loinc_code", loincCode);
        m.put("local_code", localCode);
        m.put("display_name", displayName);
        m.put("category", category);
        m.put("value", value);
        m.put("unit", unit);
        m.put("interpretation", code == null ? null : code.name());
        m.put("fhir_interpretation_code", code == null ? null : code.fhirCode());
        m.put("abnormal", code != null && code.isAbnormal());
        m.put("critical", code != null && code.isCritical());
        m.put("reference_range", range == null ? null : range.toMap());
        m.put("delta", delta);
        m.put("trend", trend == null ? null : trend.name());
        m.put("note", note);
        m.put("advisory", true);
        return m;
    }
}
