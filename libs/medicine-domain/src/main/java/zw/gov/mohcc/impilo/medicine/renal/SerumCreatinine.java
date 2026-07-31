package zw.gov.mohcc.impilo.medicine.renal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * How a recorded serum creatinine is recognised and brought to the unit {@link EgfrCalculator}
 * expects.
 *
 * <p>This exists because two services derive an eGFR from stored observations — the Clinical
 * Knowledge Platform for its rules engines, pct-service for the fact map behind form applicability
 * — and both must recognise the same analyte and read the same units the same way. Duplicating that
 * would mean one service could quietly start disagreeing with the other about what a patient's
 * kidney function is, which is the kind of divergence nobody notices until a dose is wrong.</p>
 *
 * <p>Unit handling is deliberately strict. {@link #toMicromolPerLitre} returns null for a unit it
 * does not recognise rather than assuming µmol/L: a creatinine of 11.3 recorded in mg/L, read as
 * µmol/L, reports a filtration rate in the hundreds for a patient in renal failure. Refusing to
 * guess costs a question; guessing costs the answer.</p>
 */
public final class SerumCreatinine {

    /**
     * LOINC codes for serum or plasma creatinine. {@code 2160-0} is the form Zimbabwean laboratories
     * report; the others appear in imported and FHIR-sourced records.
     */
    public static final List<String> LOINC_CODES =
            List.of("2160-0", "38483-4", "14682-9", "77140-2");

    private SerumCreatinine() {
    }

    /** True when the code is one of {@link #LOINC_CODES}. Null and blank are false. */
    public static boolean isCreatinineCode(String loincCode) {
        return loincCode != null && LOINC_CODES.contains(loincCode.trim());
    }

    /**
     * True when an observation's display name is a serum creatinine.
     *
     * <p>Excludes creatinine clearance and the protein/creatinine ratio, which are different
     * analytes in different units whose names collide on a naive contains-match. A clearance of 80
     * mL/min read as a creatinine of 80 µmol/L happens to look plausible, which is what makes the
     * collision dangerous rather than merely wrong.</p>
     */
    public static boolean isCreatinineName(String displayName) {
        if (displayName == null) {
            return false;
        }
        String name = displayName.toLowerCase(Locale.ROOT);
        return name.contains("creatinine")
                && !name.contains("clearance")
                && !name.contains("ratio");
    }

    /**
     * A recorded creatinine converted to µmol/L, or null when it cannot be read.
     *
     * <p>Null means the value or its unit could not be trusted — no value, or a unit that is neither
     * µmol/L nor mg/dL. Callers must treat that as "no creatinine" and leave any derived eGFR
     * absent.</p>
     *
     * @param value the recorded number
     * @param unit  the recorded unit; µmol/L in any of its spellings, or mg/dL, are understood
     */
    public static BigDecimal toMicromolPerLitre(BigDecimal value, String unit) {
        if (value == null) {
            return null;
        }
        String u = unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT);
        if (u.startsWith("umol") || u.startsWith("µmol") || u.startsWith("μmol")) {
            return value;
        }
        if (u.startsWith("mg/dl") || u.startsWith("mg/100")) {
            return value.multiply(EgfrCalculator.MICROMOL_PER_LITRE_PER_MG_PER_DL);
        }
        return null;
    }
}
