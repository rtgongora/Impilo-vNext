package zw.gov.mohcc.impilo.experience.clinical;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Translates between the vitals wire contract the experience layer speaks and the discrete coded
 * observations pct-service records ({@code pct_observations}, the doctrine home for vitals).
 *
 * <p>One clinical truth: pct serves observations, and only observations. A "vitals set" — the
 * nurse's one contact that produces a blood pressure, a temperature and a saturation together —
 * is an experience-layer composition. On write, a set becomes one observation per recorded value,
 * batched so pct applies them in one transaction, sharing an {@code effective_at} and a
 * {@code VITALS_SET} provenance id. On read, observations are grouped back into sets by that
 * provenance id (falling back to encounter + effective time for observations recorded by other
 * writers, e.g. form extraction), so the vitals surface shows every vital sign however it was
 * captured.</p>
 *
 * <p>Composed rows carry every attribute in both snake_case and camelCase. The consumers of
 * {@code /internal/v1/vitals} historically disagreed on the dialect — and since the old
 * {@code /v1/vitals} path was never served by anything, neither group has ever seen data, so
 * there is no single working contract to preserve. Emitting both spellings makes every existing
 * consumer read the same truth without a coordinated UI migration.</p>
 */
@Component
public class VitalsObservationBridge {

    /** Provenance type stamped on every observation written as part of one vitals set. */
    public static final String VITALS_SET = "VITALS_SET";

    private record VitalField(String key, String camel, String loinc, String unit, String display,
                              List<String> aliases) {
    }

    /**
     * The vital-sign vocabulary: wire field ↔ observation code. Writes use LOINC with the same
     * codes, units and displays the form-extraction mappings already record (seed-forms
     * {@code 01-opd-triage} et al.) — the estate's governed name for "systolic blood pressure" is
     * {@code 8480-6}, and a second code for the same clinical question would fork the vocabulary.
     * Reads additionally accept the wire key and legacy uppercase spellings so vitals recorded by
     * any writer still reach the surface.
     */
    private static final List<VitalField> FIELDS = List.of(
            new VitalField("systolic", "systolic", "8480-6", "mm[Hg]", "Systolic blood pressure",
                    List.of("SYSTOLIC", "SYSTOLIC_BP", "SYSTOLIC_BLOOD_PRESSURE")),
            new VitalField("diastolic", "diastolic", "8462-4", "mm[Hg]", "Diastolic blood pressure",
                    List.of("DIASTOLIC", "DIASTOLIC_BP", "DIASTOLIC_BLOOD_PRESSURE")),
            new VitalField("heart_rate", "heartRate", "8867-4", "/min", "Heart rate",
                    List.of("HEART_RATE", "PULSE", "PULSE_RATE")),
            new VitalField("temperature", "temperature", "8310-5", "Cel", "Body temperature",
                    List.of("TEMPERATURE", "BODY_TEMPERATURE")),
            new VitalField("respiratory_rate", "respiratoryRate", "9279-1", "/min", "Respiratory rate",
                    List.of("RESPIRATORY_RATE", "RESP_RATE", "RR")),
            new VitalField("oxygen_saturation", "oxygenSaturation", "59408-5", "%", "Oxygen saturation",
                    List.of("OXYGEN_SATURATION", "SPO2", "O2_SATURATION")),
            new VitalField("weight", "weight", "29463-7", "kg", "Body weight",
                    List.of("WEIGHT", "BODY_WEIGHT")),
            new VitalField("height", "height", "8302-2", "cm", "Body height",
                    List.of("HEIGHT", "BODY_HEIGHT")),
            new VitalField("pain_score", "painScore", "72514-3", "{score}", "Pain severity score",
                    List.of("PAIN_SCORE", "PAIN")));

    private static final String LOINC = "http://loinc.org";

    /**
     * A flat vitals-set body (either dialect) → one observation body per present value, sharing
     * an effective time and a freshly minted {@code VITALS_SET} provenance id.
     *
     * @return the observation bodies; empty when the request carries no vital sign at all
     */
    public List<Map<String, Object>> toObservationBodies(Map<String, Object> flat) {
        String setId = UUID.randomUUID().toString();
        String effectiveAt = OffsetDateTime.now().toString();
        String patientId = str(flat, "patient_id", "patientId");
        String encounterId = str(flat, "encounter_id", "encounterId");
        String recordedBy = str(flat, "recorded_by", "recordedBy");
        String notes = str(flat, "notes");

        List<Map<String, Object>> out = new ArrayList<>();
        for (VitalField field : FIELDS) {
            BigDecimal value = decimal(flat, field.key(), field.camel());
            if (value == null) {
                continue;
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("patient_id", patientId);
            if (encounterId != null) body.put("encounter_id", encounterId);
            if (recordedBy != null) body.put("recorded_by", recordedBy);
            body.put("code", field.loinc());
            body.put("code_system", LOINC);
            body.put("display", field.display());
            body.put("category", "VITAL_SIGNS");
            body.put("source", "MANUAL");
            body.put("value_quantity", value);
            body.put("value_unit", field.unit());
            body.put("effective_at", effectiveAt);
            body.put("derived_from_type", VITALS_SET);
            body.put("derived_from_id", setId);
            if (notes != null) body.put("notes", notes);
            out.add(body);
        }
        return out;
    }

    /**
     * A single mobile vital ({@code vital_type} + value) → one observation body. Known types map
     * to the canonical vocabulary; an unknown type is still recorded, under its own lower-cased
     * code, rather than demoted to a free-text note that nothing can ever read back.
     */
    public Map<String, Object> toSingleObservationBody(String patientId, String encounterId,
                                                       String vitalType, BigDecimal value,
                                                       String unit, String notes) {
        VitalField field = fieldForCode(vitalType);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patient_id", patientId);
        if (encounterId != null && !encounterId.isBlank()) body.put("encounter_id", encounterId);
        if (field != null) {
            body.put("code", field.loinc());
            body.put("code_system", LOINC);
            body.put("display", field.display());
        } else {
            body.put("code", vitalType.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_"));
        }
        body.put("category", "VITAL_SIGNS");
        body.put("source", "MANUAL");
        body.put("value_quantity", value);
        body.put("value_unit", unit != null && !unit.isBlank() ? unit
                : field != null ? field.unit() : "unit");
        body.put("effective_at", OffsetDateTime.now().toString());
        if (notes != null && !notes.isBlank()) body.put("notes", notes);
        return body;
    }

    /**
     * Observations → composed vitals-set rows, newest first. Voided rows are excluded; rows that
     * are not vital signs (labs, survey answers) are excluded; everything else is grouped by its
     * {@code VITALS_SET} provenance, or by encounter + effective time when it was recorded by a
     * different writer.
     */
    public List<Map<String, Object>> composeSets(JsonNode observations) {
        Map<String, List<JsonNode>> groups = new LinkedHashMap<>();
        if (observations != null && observations.isArray()) {
            for (JsonNode obs : observations) {
                if (!isLiveVitalSign(obs)) {
                    continue;
                }
                String key = VITALS_SET.equals(obs.path("derived_from_type").asText(null))
                        ? obs.path("derived_from_id").asText()
                        : obs.path("encounter_id").asText("") + "|" + obs.path("effective_at").asText("");
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(obs);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>(groups.size());
        for (List<JsonNode> group : groups.values()) {
            rows.add(composeSetRow(group));
        }
        rows.sort(Comparator.comparing(
                row -> parseTime((String) ((Map<?, ?>) row.get("attributes")).get("effective_at")),
                Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    /** One group of observations (one contact) → one JSON:API vitals row, both dialects. */
    public Map<String, Object> composeSetRow(List<JsonNode> group) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        JsonNode first = group.get(0);
        putBoth(attributes, "patient_id", "patientId", text(first, "patient_id", "subject_cpid"));
        putBoth(attributes, "encounter_id", "encounterId", text(first, "encounter_id"));
        putBoth(attributes, "recorded_by", "recordedBy", text(first, "recorded_by"));

        String notes = null;
        String recordedAt = null;
        String effectiveAt = null;
        for (JsonNode obs : group) {
            String code = obs.path("code").asText("");
            VitalField field = fieldForCode(code);
            if (field != null && obs.hasNonNull("value_quantity")) {
                putBoth(attributes, field.key(), field.camel(), obs.get("value_quantity").decimalValue());
            }
            if (notes == null) notes = text(obs, "notes");
            if (recordedAt == null) recordedAt = text(obs, "recorded_at");
            if (effectiveAt == null) effectiveAt = text(obs, "effective_at");
        }
        BigDecimal weight = (BigDecimal) attributes.get("weight");
        BigDecimal height = (BigDecimal) attributes.get("height");
        if (weight != null && height != null && height.signum() > 0) {
            BigDecimal metres = height.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            attributes.put("bmi", weight.divide(metres.multiply(metres), 1, RoundingMode.HALF_UP));
        }
        attributes.put("notes", notes);
        putBoth(attributes, "recorded_at", "recordedAt", recordedAt);
        putBoth(attributes, "created_at", "createdAt", recordedAt);
        putBoth(attributes, "effective_at", "effectiveAt", effectiveAt);

        String setId = VITALS_SET.equals(first.path("derived_from_type").asText(null))
                ? first.path("derived_from_id").asText()
                : first.path("observation_id").asText();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", setId);
        row.put("type", "vitals");
        row.put("attributes", attributes);
        return row;
    }

    /**
     * Observation → one flat mobile vital row. The two mobile consumers of the same endpoint
     * disagree on the envelope (one reads {@code attributes.*}, one reads the row flat), so the
     * row carries both: the flat keys and an {@code attributes} mirror.
     */
    public Map<String, Object> toMobileRow(JsonNode obs) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        String code = obs.path("code").asText("");
        VitalField field = fieldForCode(code);
        String vitalType = (field != null ? field.key() : code).toUpperCase(Locale.ROOT);
        putBoth(attributes, "encounter_id", "encounterId", text(obs, "encounter_id"));
        putBoth(attributes, "vital_type", "vitalType", vitalType);
        attributes.put("value", obs.hasNonNull("value_quantity") ? obs.get("value_quantity").decimalValue() : null);
        attributes.put("unit", text(obs, "value_unit"));
        putBoth(attributes, "measured_at", "measuredAt", text(obs, "effective_at"));
        putBoth(attributes, "measured_by", "measuredBy", text(obs, "recorded_by"));

        Map<String, Object> row = new LinkedHashMap<>(attributes);
        row.put("id", obs.path("observation_id").asText());
        row.put("type", "Vital");
        row.put("attributes", attributes);
        return row;
    }

    /** True when the observation is a non-voided vital sign with a quantity. */
    public boolean isLiveVitalSign(JsonNode obs) {
        if (obs == null || "ENTERED_IN_ERROR".equals(obs.path("status").asText(""))) {
            return false;
        }
        return fieldForCode(obs.path("code").asText("")) != null
                || "VITAL_SIGNS".equals(obs.path("category").asText(""));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Resolves a code in any of its spellings: LOINC, wire key, or a legacy uppercase alias. */
    private static VitalField fieldForCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (VitalField field : FIELDS) {
            if (field.loinc().equals(code.trim())
                    || field.key().equalsIgnoreCase(code.trim())
                    || field.aliases().contains(normalized)) {
                return field;
            }
        }
        return null;
    }

    private static void putBoth(Map<String, Object> attributes, String snake, String camel, Object value) {
        attributes.put(snake, value);
        if (!snake.equals(camel)) {
            attributes.put(camel, value);
        }
    }

    private static String text(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.hasNonNull(key) && !node.get(key).asText().isBlank()) {
                return node.get(key).asText();
            }
        }
        return null;
    }

    private static String str(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private static BigDecimal decimal(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof Number n) {
                return new BigDecimal(n.toString());
            }
            if (value != null && !String.valueOf(value).isBlank()) {
                try {
                    return new BigDecimal(String.valueOf(value).trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(key + " is not a number: " + value);
                }
            }
        }
        return null;
    }

    private static OffsetDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
