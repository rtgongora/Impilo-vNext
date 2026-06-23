package zw.gov.mohcc.impilo.clinical.rules.model;

/**
 * Normalised medication line for deterministic rule evaluation.
 */
public record MedicationLine(
        String genericName,
        String displayName,
        Double doseMg,
        String route,
        Integer daysOnTherapy,
        boolean broadSpectrumAntibiotic,
        // National-formulary specialist-only designation, supplied by the calling
        // system (e.g. from MedicineGuidance) — used by level-of-care gating.
        boolean specialistOnly
) {
}
