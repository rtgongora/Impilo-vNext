package zw.gov.mohcc.impilo.medicine.renal;

import java.math.BigDecimal;

/**
 * KDIGO GFR categories G1–G5, including the G3a/G3b split.
 *
 * <p>Source: KDIGO 2012 Clinical Practice Guideline for the Evaluation and Management of Chronic
 * Kidney Disease, Table 6 (GFR categories), carried forward unchanged in the KDIGO 2024 CKD
 * guideline. The primary text is <strong>not vendored</strong> under {@code docs/reference}; these
 * boundaries are transcribed from the published category table and are engineering seeds pending
 * MoHCC ratification.</p>
 *
 * <p>This is a classification, not a decision. Naming the band an eGFR falls into is arithmetic —
 * the boundaries are fixed in the published guideline and are the same in every country. What to
 * do at each band (refer, adjust a dose, repeat the test, add a diagnosis to the problem list) is
 * policy and belongs in governed CKP content, not here.</p>
 *
 * <p><strong>A GFR category is not a diagnosis of chronic kidney disease.</strong> CKD requires the
 * abnormality to have been present for more than three months, and G1 and G2 are not CKD at all in
 * the absence of a marker of kidney damage such as albuminuria. A single eGFR result carries none
 * of that. The full KDIGO staging is a grid of GFR category against albuminuria category (A1–A3);
 * this enum is one axis of it, and callers must not present the axis as the grid.</p>
 */
public enum KdigoStage {

    /** G1 — GFR 90 or above: normal or high. Not CKD without a marker of kidney damage. */
    G1("G1", "Normal or high", 90, null),

    /** G2 — GFR 60 to 89: mildly decreased. Not CKD without a marker of kidney damage. */
    G2("G2", "Mildly decreased", 60, 89),

    /** G3a — GFR 45 to 59: mildly to moderately decreased. */
    G3A("G3a", "Mildly to moderately decreased", 45, 59),

    /** G3b — GFR 30 to 44: moderately to severely decreased. */
    G3B("G3b", "Moderately to severely decreased", 30, 44),

    /** G4 — GFR 15 to 29: severely decreased. */
    G4("G4", "Severely decreased", 15, 29),

    /** G5 — GFR below 15: kidney failure. */
    G5("G5", "Kidney failure", null, 14);

    private final String code;
    private final String description;
    private final Integer lowerBoundInclusive;
    private final Integer upperBoundInclusive;

    KdigoStage(String code, String description, Integer lowerBoundInclusive, Integer upperBoundInclusive) {
        this.code = code;
        this.description = description;
        this.lowerBoundInclusive = lowerBoundInclusive;
        this.upperBoundInclusive = upperBoundInclusive;
    }

    /** The published category code, including the lower-case a/b of the G3 split. */
    public String code() {
        return code;
    }

    /** The published description of the category, as written in the KDIGO table. */
    public String description() {
        return description;
    }

    /** Lower bound of the category in mL/min/1.73 m², inclusive; null for the open-ended G5. */
    public Integer lowerBoundInclusive() {
        return lowerBoundInclusive;
    }

    /** Upper bound of the category in mL/min/1.73 m², inclusive; null for the open-ended G1. */
    public Integer upperBoundInclusive() {
        return upperBoundInclusive;
    }

    /**
     * The KDIGO GFR category an eGFR falls into.
     *
     * <p>Returns null when there is no eGFR to categorise or the value is not a possible GFR.
     * Null here means "no category", never G5 — a missing result and total kidney failure must
     * never render the same way.</p>
     *
     * <p>The boundaries are compared on the value as given. Rounding an eGFR before calling this
     * can move a result across a boundary (59.5 rounds to 60 and reads as G2 rather than G3a), so
     * {@link EgfrCalculator} categorises the rounded value it publishes, and both travel together
     * in the same result so a stored category is always the one that was shown.</p>
     */
    public static KdigoStage fromEgfr(BigDecimal egfr) {
        if (egfr == null || egfr.signum() < 0) {
            return null;
        }
        double value = egfr.doubleValue();
        if (value >= 90.0d) {
            return G1;
        }
        if (value >= 60.0d) {
            return G2;
        }
        if (value >= 45.0d) {
            return G3A;
        }
        if (value >= 30.0d) {
            return G3B;
        }
        if (value >= 15.0d) {
            return G4;
        }
        return G5;
    }
}
