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
        boolean broadSpectrumAntibiotic
) {
}
