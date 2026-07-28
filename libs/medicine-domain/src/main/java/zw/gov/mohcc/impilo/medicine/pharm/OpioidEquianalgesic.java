package zw.gov.mohcc.impilo.medicine.pharm;

import zw.gov.mohcc.impilo.medicine.common.RefusalReason;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Oral morphine equivalent (OME) conversion for the opioids on the Zimbabwe essential medicines list.
 *
 * <p><strong>Sources.</strong> Faculty of Pain Medicine of the Royal College of Anaesthetists,
 * <em>Opioids Aware — dose equivalence and changing opioids</em> (the "÷10 / ×1.5" table used across
 * UK and Commonwealth practice); WHO <em>Guidelines for the pharmacological and radiotherapeutic
 * management of cancer pain</em> (2018); and the transdermal fentanyl equivalence commonly published
 * as 25 µg/h ≈ 90 mg oral morphine per 24 hours. <strong>None of these are vendored</strong> under
 * {@code docs/reference}. The factors below are transcribed engineering seeds pending MoHCC
 * ratification, and one of them (codeine) is a point of genuine disagreement between published
 * tables — see {@link Opioid#ORAL_CODEINE}.</p>
 *
 * <h2>What this class computes, and the much larger thing it does not</h2>
 *
 * <p>It computes a total daily oral morphine equivalent: a comparison figure, used to describe how
 * much opioid a person is on. That is arithmetic.</p>
 *
 * <p>It does <strong>not</strong> produce a switching dose. Converting a patient from one opioid to
 * another is not the same calculation: incomplete cross-tolerance means the calculated equivalent
 * dose of the new opioid is routinely reduced by 25–50% before prescribing, and how much to reduce
 * by depends on the patient, the reason for switching and the setting. That reduction is a clinical
 * decision, it is policy, and it lives in governed CKP content. <strong>A number out of this class
 * used directly as a prescription is an overdose.</strong></p>
 *
 * <p>Nor is the conversion symmetric. Published tables give different factors depending on the
 * direction of the switch, and this class only ever converts <em>to</em> oral morphine. There is no
 * inverse method and one must not be added by dividing.</p>
 *
 * <h2>Methadone</h2>
 *
 * <p>{@link Opioid#ORAL_METHADONE} carries no factor at all and every conversion involving it is
 * refused with {@link RefusalReason#REQUIRES_SPECIALIST_CALCULATION}. This is the single most
 * important behaviour in the class. Methadone's ratio to morphine is dose-dependent and strongly
 * non-linear — published ladders run from roughly 4:1 at low morphine doses to 20:1 or more above
 * 1000 mg/day — and it has a long, variable half-life with QT effects, so it accumulates for days
 * after a switch. Any single number here would be badly wrong at one end of the range and fatal at
 * the other. A refusal that sends the prescriber to a palliative care or pain specialist is the
 * correct output.</p>
 */
public final class OpioidEquianalgesic {

    /** Bumped whenever a factor, unit or ceiling changes, because stored totals move with it. */
    public static final String CONTENT_VERSION = "impilo-ome-1.0.0";

    /** The unit every equivalent produced by this class is expressed in. */
    public static final String UNIT = "mg oral morphine per 24 hours";

    private OpioidEquianalgesic() {
    }

    /**
     * The opioids this class can be asked about, each with its dose unit, its conversion factor to
     * oral morphine, and the published source that factor came from.
     *
     * <p>A null {@link #oralMorphineFactor()} is not "unknown" — it means the conversion exists but
     * is not a multiplication. Only methadone is in that state today.</p>
     */
    public enum Opioid {

        /** The reference opioid. Factor 1 by definition. */
        ORAL_MORPHINE("oral morphine", "mg/24h", 1.0d, BigDecimal.valueOf(5000),
                "Reference opioid; factor is 1 by definition."),

        /**
         * Oral codeine, factor 0.1 (codeine 240 mg/24h ≈ morphine 24 mg/24h).
         *
         * <p><strong>Published tables disagree here.</strong> The Faculty of Pain Medicine table used
         * as this library's primary source divides codeine by 10. The US CDC morphine milligram
         * equivalent table uses 0.15. The 0.1 factor is the more conservative of the two — it reports
         * a lower equivalent for the same codeine dose — and is the one aligned with UK and
         * Commonwealth practice, which is closer to Zimbabwean prescribing. A national ratification
         * that picks 0.15 changes every stored total, which is why the factor is versioned.</p>
         */
        ORAL_CODEINE("oral codeine", "mg/24h", 0.1d, BigDecimal.valueOf(2000),
                "Faculty of Pain Medicine (RCoA) Opioids Aware dose equivalence table: codeine ÷ 10."
                        + " The US CDC MME table uses 0.15 for the same conversion."),

        /**
         * Oral tramadol, factor 0.1 (tramadol 100 mg/24h ≈ morphine 10 mg/24h).
         *
         * <p>Tramadol is only partly an opioid — much of its effect is serotonergic and noradrenergic
         * — so an oral morphine equivalent describes only part of what it is doing, and says nothing
         * about its serotonin-syndrome and seizure interactions. The number is a comparison figure,
         * not a summary of the drug.</p>
         */
        ORAL_TRAMADOL("oral tramadol", "mg/24h", 0.1d, BigDecimal.valueOf(1200),
                "Faculty of Pain Medicine (RCoA) Opioids Aware dose equivalence table: tramadol ÷ 10."),

        /**
         * Oral oxycodone, factor 1.5 (oxycodone 10 mg/24h ≈ morphine 15 mg/24h).
         *
         * <p>Published ratios range from 1.5 to 2. The lower end is used here because it reports a
         * smaller equivalent, which is the safer direction of error for a comparison figure.</p>
         */
        ORAL_OXYCODONE("oral oxycodone", "mg/24h", 1.5d, BigDecimal.valueOf(2000),
                "Faculty of Pain Medicine (RCoA) Opioids Aware dose equivalence table: oxycodone × 1.5."
                        + " Published ratios range 1.5–2."),

        /**
         * Transdermal fentanyl, dosed in micrograms per hour, factor 3.6 mg oral morphine per 24
         * hours per µg/h (25 µg/h ≈ 90 mg/24h).
         *
         * <p><strong>The unit is not milligrams and not per-day.</strong> A patch strength entered as
         * a daily milligram dose produces a nonsense equivalent, which is why the unit travels with
         * the enum constant and why the plausibility ceiling is expressed in µg/h.</p>
         *
         * <p>The manufacturer's own conversion table is more conservative and broader — it maps a
         * 25 µg/h patch to a range of roughly 60–134 mg/24h of oral morphine, because it is written
         * to avoid overdosing on the way onto the patch. A single midpoint factor is appropriate for
         * describing a total burden and is not appropriate for choosing a patch strength.</p>
         */
        TRANSDERMAL_FENTANYL("transdermal fentanyl", "microgram/hour", 3.6d, BigDecimal.valueOf(1000),
                "Commonly published transdermal equivalence: fentanyl 25 µg/h ≈ 90 mg oral morphine per"
                        + " 24 hours. Manufacturer tables give a range (approx. 60–134 mg) rather than a"
                        + " point value."),

        /**
         * Oral methadone. <strong>No factor exists and none will be added.</strong>
         *
         * <p>See the class javadoc. The ratio to morphine rises with the dose being converted from,
         * from roughly 4:1 to 20:1 or beyond, and methadone accumulates over days. Every conversion
         * involving it is refused with {@link RefusalReason#REQUIRES_SPECIALIST_CALCULATION}.</p>
         */
        ORAL_METHADONE("oral methadone", "mg/24h", null, BigDecimal.valueOf(1000),
                "No linear factor. Methadone's morphine ratio is dose-dependent and non-linear;"
                        + " conversion requires specialist calculation.");

        private final String displayName;
        private final String doseUnit;
        private final Double oralMorphineFactor;
        private final BigDecimal implausibleDoseCeiling;
        private final String source;

        Opioid(String displayName, String doseUnit, Double oralMorphineFactor,
               BigDecimal implausibleDoseCeiling, String source) {
            this.displayName = displayName;
            this.doseUnit = doseUnit;
            this.oralMorphineFactor = oralMorphineFactor;
            this.implausibleDoseCeiling = implausibleDoseCeiling;
            this.source = source;
        }

        /** The opioid and route as it would be written on a chart. */
        public String displayName() {
            return displayName;
        }

        /** The unit a dose of this opioid must be supplied in. Fentanyl is µg/h, not mg/24h. */
        public String doseUnit() {
            return doseUnit;
        }

        /**
         * Multiplier onto mg oral morphine per 24 hours, or null when no linear factor exists.
         *
         * <p>Null means "requires specialist calculation", not "unknown". A caller that treats null
         * as 1, or as 0, or skips the drug silently, has reintroduced exactly the hazard this design
         * exists to prevent.</p>
         */
        public Double oralMorphineFactor() {
            return oralMorphineFactor;
        }

        /**
         * A dose above this is refused as a probable transcription error.
         *
         * <p><strong>Not a maximum dose.</strong> Maximum doses are prescribing policy and live in
         * governed CKP content; these ceilings sit far above any therapeutic dose and exist only to
         * catch a misplaced decimal point or a patch strength typed into a milligram field.</p>
         */
        public BigDecimal implausibleDoseCeiling() {
            return implausibleDoseCeiling;
        }

        /** The published table this constant's factor was transcribed from. Not vendored. */
        public String source() {
            return source;
        }

        /** True when a single multiplication converts this opioid to oral morphine. */
        public boolean linearlyConvertible() {
            return oralMorphineFactor != null;
        }
    }

    /**
     * The oral morphine equivalent of one opioid at one daily dose.
     *
     * <p>Sources per {@link Opioid}; none vendored under {@code docs/reference}; engineering seeds
     * pending MoHCC ratification. The result is a comparison figure and never a switching dose —
     * see the class javadoc on incomplete cross-tolerance.</p>
     *
     * @param opioid     which opioid; null is refused
     * @param dosePer24h the dose in that opioid's own {@link Opioid#doseUnit()} — µg/h for the
     *                   fentanyl patch, mg/24h for everything else
     */
    public static Conversion oralMorphineEquivalent(Opioid opioid, BigDecimal dosePer24h) {
        if (opioid == null) {
            return new Conversion(null, null, dosePer24h, null, RefusalReason.INPUT_MISSING,
                    "No opioid was identified, so no equivalent can be computed.", CONTENT_VERSION);
        }
        if (dosePer24h == null) {
            return refused(opioid, null, RefusalReason.INPUT_MISSING,
                    "No dose was recorded for " + opioid.displayName() + ", so its oral morphine"
                            + " equivalent cannot be computed. A missing dose is not a dose of zero.");
        }
        if (dosePer24h.signum() <= 0) {
            return refused(opioid, dosePer24h, RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "A dose of " + dosePer24h.toPlainString() + " " + opioid.doseUnit() + " was recorded"
                            + " for " + opioid.displayName() + ". A dose of zero or below is not a"
                            + " prescription; if the opioid has stopped, leave it out of the total.");
        }
        if (dosePer24h.compareTo(opioid.implausibleDoseCeiling()) > 0) {
            return refused(opioid, dosePer24h, RefusalReason.INPUT_OUT_OF_PHYSIOLOGICAL_RANGE,
                    "A dose of " + dosePer24h.toPlainString() + " " + opioid.doseUnit() + " for "
                            + opioid.displayName() + " is above the plausibility ceiling of "
                            + opioid.implausibleDoseCeiling().toPlainString() + " " + opioid.doseUnit()
                            + ". This is a transcription guard, not a maximum dose — check the units and"
                            + " the decimal point.");
        }
        if (!opioid.linearlyConvertible()) {
            return refused(opioid, dosePer24h, RefusalReason.REQUIRES_SPECIALIST_CALCULATION,
                    opioid.displayName() + " has no linear oral morphine equivalent. Its ratio to"
                            + " morphine rises with the dose being converted from — roughly 4:1 at low"
                            + " doses to 20:1 or more at high ones — and it accumulates over days, so a"
                            + " fixed factor is badly wrong at one end of the range and dangerous at the"
                            + " other. Refer to palliative care or pain specialist advice for the"
                            + " conversion.");
        }

        BigDecimal ome = dosePer24h
                .multiply(BigDecimal.valueOf(opioid.oralMorphineFactor()))
                .setScale(1, RoundingMode.HALF_UP);
        return new Conversion(ome, opioid, dosePer24h, opioid.oralMorphineFactor(), null, null,
                CONTENT_VERSION);
    }

    /**
     * The total oral morphine equivalent of a regimen of several opioids.
     *
     * <p><strong>The total is null unless every item converted.</strong> A regimen that includes
     * methadone, or an opioid with a missing dose, has no total — and the partial sum is exposed
     * separately under a name nobody can mistake for one. A partial sum labelled "total daily OME"
     * is the exact failure this design exists to prevent: it understates the patient's opioid
     * burden by whatever was dropped, and the item most likely to be dropped is methadone, which is
     * usually the largest contributor in the regimen.</p>
     *
     * @param doses dose per opioid, in each opioid's own unit; iteration order is preserved so the
     *              returned lists read in the order the regimen was given
     */
    public static Total totalOralMorphineEquivalent(Map<Opioid, BigDecimal> doses) {
        if (doses == null || doses.isEmpty()) {
            return new Total(null, BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP), List.of(), List.of(),
                    false, RefusalReason.INPUT_MISSING,
                    "No opioids were supplied, so there is no total to compute. This is not a total of"
                            + " zero: a patient with no recorded opioids and a patient on none are"
                            + " different records.",
                    CONTENT_VERSION);
        }

        Map<Opioid, BigDecimal> ordered = new LinkedHashMap<>(doses);
        List<Conversion> converted = new ArrayList<>();
        List<Conversion> notConverted = new ArrayList<>();
        BigDecimal partial = BigDecimal.ZERO;

        for (Map.Entry<Opioid, BigDecimal> entry : ordered.entrySet()) {
            Conversion conversion = oralMorphineEquivalent(entry.getKey(), entry.getValue());
            if (conversion.computed()) {
                converted.add(conversion);
                partial = partial.add(conversion.oralMorphineEquivalentMgPer24h());
            } else {
                notConverted.add(conversion);
            }
        }

        BigDecimal partialSum = partial.setScale(1, RoundingMode.HALF_UP);
        if (!notConverted.isEmpty()) {
            StringBuilder unconverted = new StringBuilder();
            for (Conversion conversion : notConverted) {
                if (unconverted.length() > 0) {
                    unconverted.append("; ");
                }
                unconverted.append(conversion.opioid() == null
                        ? "an unidentified opioid" : conversion.opioid().displayName());
            }
            return new Total(null, partialSum, List.copyOf(converted), List.copyOf(notConverted), false,
                    RefusalReason.REQUIRES_SPECIALIST_CALCULATION,
                    "No total daily oral morphine equivalent can be given: " + unconverted
                            + " could not be converted. The partial sum of the remaining opioids is"
                            + " available separately and understates this patient's opioid burden;"
                            + " it must not be shown as a total.",
                    CONTENT_VERSION);
        }

        return new Total(partialSum, partialSum, List.copyOf(converted), List.of(), true, null, null,
                CONTENT_VERSION);
    }

    private static Conversion refused(Opioid opioid, BigDecimal dose, RefusalReason reason, String explanation) {
        return new Conversion(null, opioid, dose, opioid == null ? null : opioid.oralMorphineFactor(),
                reason, explanation, CONTENT_VERSION);
    }

    /**
     * One opioid's oral morphine equivalent, or a stated reason there is none.
     *
     * @param oralMorphineEquivalentMgPer24h the equivalent in {@link #UNIT}; null when refused
     * @param opioid                         the opioid asked about; null only when none was identified
     * @param dose                           the dose as supplied, in the opioid's own unit
     * @param factorApplied                  the multiplier used; null when none exists or none was applied
     * @param reason                         machine-readable refusal code; null when computed
     * @param explanation                    clinician-readable refusal text; null when computed
     * @param contentVersion                 the {@link #CONTENT_VERSION} that produced this result
     */
    public record Conversion(BigDecimal oralMorphineEquivalentMgPer24h,
                             Opioid opioid,
                             BigDecimal dose,
                             Double factorApplied,
                             RefusalReason reason,
                             String explanation,
                             String contentVersion) {

        /** True when an equivalent is present. False means {@link #reason()} explains why not. */
        public boolean computed() {
            return oralMorphineEquivalentMgPer24h != null;
        }
    }

    /**
     * A regimen's total oral morphine equivalent, or a stated reason there is none.
     *
     * @param oralMorphineEquivalentMgPer24h        the total in {@link #UNIT}; <strong>null unless
     *                                              every opioid converted</strong>
     * @param partialSumExcludingUnconvertedMgPer24h the sum of those that did convert. Deliberately
     *                                              named so it cannot be mistaken for a total. It
     *                                              understates the regimen whenever
     *                                              {@code notConverted} is non-empty.
     * @param converted                             per-opioid results that produced a figure
     * @param notConverted                          per-opioid results that did not, each carrying its reason
     * @param complete                              true only when {@code notConverted} is empty
     * @param reason                                machine-readable refusal code; null when complete
     * @param explanation                           clinician-readable refusal text; null when complete
     * @param contentVersion                        the {@link #CONTENT_VERSION} that produced this result
     */
    public record Total(BigDecimal oralMorphineEquivalentMgPer24h,
                        BigDecimal partialSumExcludingUnconvertedMgPer24h,
                        List<Conversion> converted,
                        List<Conversion> notConverted,
                        boolean complete,
                        RefusalReason reason,
                        String explanation,
                        String contentVersion) {

        /** True when a total is present, which is exactly when every opioid converted. */
        public boolean computed() {
            return oralMorphineEquivalentMgPer24h != null;
        }
    }
}
