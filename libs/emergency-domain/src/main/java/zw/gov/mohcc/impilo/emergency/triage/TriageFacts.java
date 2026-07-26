package zw.gov.mohcc.impilo.emergency.triage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What was actually assessed about this patient, in three-valued logic.
 *
 * <p><b>Absent is UNKNOWN, never false.</b> That distinction is the whole safety argument of this
 * class. A sign that was not looked for is not a sign that was absent, and an unmeasured
 * observation is not a normal one. The engine this replaces conflated them — {@code intVal()}
 * returned {@code 0} for a missing key and every comparison was then guarded with {@code hr > 0 &&},
 * so an unmeasured patient scored as not-in-danger. That is the reassuring default that stops
 * someone looking.
 *
 * <p>So there is deliberately no {@code getBoolean(name)} returning a primitive: a caller must
 * handle the third answer.
 */
public final class TriageFacts {

    private final Map<String, Boolean> signs;
    private final Map<String, Double> vitals;
    private final Integer ageDays;
    private final Boolean pregnant;

    private TriageFacts(Map<String, Boolean> signs, Map<String, Double> vitals,
                        Integer ageDays, Boolean pregnant) {
        this.signs = Collections.unmodifiableMap(signs);
        this.vitals = Collections.unmodifiableMap(vitals);
        this.ageDays = ageDays;
        this.pregnant = pregnant;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** TRUE, FALSE, or UNKNOWN when the sign was never assessed. */
    public Ternary sign(String code) {
        Boolean v = signs.get(code);
        return v == null ? Ternary.UNKNOWN : (v ? Ternary.TRUE : Ternary.FALSE);
    }

    /** The measured value, or null when it was never measured. Callers must handle null. */
    public Double vital(String code) {
        return vitals.get(code);
    }

    public boolean vitalMeasured(String code) {
        return vitals.get(code) != null;
    }

    /** Age in completed days, or null when unknown. Age routes the chart, so null is consequential. */
    public Integer ageDays() {
        return ageDays;
    }

    public Ternary pregnant() {
        return pregnant == null ? Ternary.UNKNOWN : (pregnant ? Ternary.TRUE : Ternary.FALSE);
    }

    /** Every sign code that was explicitly recorded, in insertion order. */
    public Set<String> assessedSigns() {
        return new LinkedHashSet<>(signs.keySet());
    }

    /** Every vital code that was measured. */
    public Set<String> measuredVitals() {
        return new LinkedHashSet<>(vitals.keySet());
    }

    /** Three-valued truth. */
    public enum Ternary {
        TRUE, FALSE, UNKNOWN;

        public boolean isTrue() {
            return this == TRUE;
        }

        /** True when this is not a definite negative — i.e. it could still be present. */
        public boolean isNotDefinitelyAbsent() {
            return this != FALSE;
        }
    }

    public static final class Builder {
        private final Map<String, Boolean> signs = new LinkedHashMap<>();
        private final Map<String, Double> vitals = new LinkedHashMap<>();
        private Integer ageDays;
        private Boolean pregnant;

        /** Record a sign as definitely present or definitely absent. Do not call it for "not asked". */
        public Builder sign(String code, boolean present) {
            signs.put(code, present);
            return this;
        }

        /** Record a measured vital. Do not call it with a sentinel for "not measured". */
        public Builder vital(String code, double value) {
            vitals.put(code, value);
            return this;
        }

        public Builder ageDays(Integer ageDays) {
            this.ageDays = ageDays;
            return this;
        }

        public Builder pregnant(Boolean pregnant) {
            this.pregnant = pregnant;
            return this;
        }

        public TriageFacts build() {
            return new TriageFacts(signs, vitals, ageDays, pregnant);
        }
    }
}
