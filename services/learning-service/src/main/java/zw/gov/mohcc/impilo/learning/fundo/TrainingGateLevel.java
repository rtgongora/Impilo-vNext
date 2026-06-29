package zw.gov.mohcc.impilo.learning.fundo;

/**
 * Graduated enforcement level for a single training-gate requirement — the
 * "levels of permission" the gate offers a consumer (graduated-friction doctrine:
 * MINIMAL → MAXIMUM).
 *
 * <p>Boundary discipline: the level is <em>policy supplied by the caller</em> (the
 * capability's training requirement set), not invented by learning-service.
 * Learning-service computes whether each requirement is satisfied and maps the
 * unsatisfied set to a graduated decision; the PDP / consumer enforces it.
 *
 * <ul>
 *   <li>{@code ADVISORY} — surface a warning; never blocks. The conservative default.</li>
 *   <li>{@code SOFT} — should block unless explicitly overridden (supervisor ack / step-up).</li>
 *   <li>{@code HARD} — block outright until the requirement is satisfied.</li>
 * </ul>
 */
public enum TrainingGateLevel {
    ADVISORY,
    SOFT,
    HARD;

    /**
     * Conservative parse: an unknown, blank, or null level never silently escalates
     * to a block — it resolves to {@link #ADVISORY}. Hard/soft blocks must be
     * deliberately configured by the caller's policy.
     */
    public static TrainingGateLevel parse(String raw) {
        if (raw == null) {
            return ADVISORY;
        }
        switch (raw.trim().toUpperCase()) {
            case "HARD":
                return HARD;
            case "SOFT":
                return SOFT;
            default:
                return ADVISORY;
        }
    }
}
