package zw.gov.mohcc.impilo.clinical.interpretation.model;

import java.math.BigDecimal;

/**
 * A single governed reference interval drawn from a FHIR R4 {@code ObservationDefinition.qualifiedInterval}
 * (hosted in ZIBO). The applicability keys (age/sex/gestation/specimen) are matched by
 * {@code RangeResolver} against the patient context; the provenance fields ({@code artifactId},
 * {@code version}, {@code canonicalUrl}) are recorded in the audit trace for every interpretation.
 *
 * <p>All applicability keys are nullable and mean "applies to any" when null, EXCEPT that a sex- or
 * specimen-specific interval is only selected when the patient context supplies a matching value
 * (fail-closed). Pregnancy-conditioned intervals are gated OFF in Phase 1.</p>
 *
 * @param low               lower reference bound (nullable = unbounded below)
 * @param high              upper reference bound (nullable = unbounded above)
 * @param unit              UCUM unit of the bounds (required)
 * @param criticalLow       critical-low threshold (nullable)
 * @param criticalHigh      critical-high threshold (nullable)
 * @param sex               sex this interval applies to ({@link Sex#UNKNOWN}/null = any)
 * @param ageLowYears       inclusive lower age bound in years (nullable = no lower bound)
 * @param ageHighYears      exclusive upper age bound in years (nullable = no upper bound)
 * @param pregnancy         true if this is a pregnancy-specific interval (gated off Phase 1)
 * @param gestationLowWeeks inclusive lower gestational-age bound (nullable)
 * @param gestationHighWeeks exclusive upper gestational-age bound (nullable)
 * @param specimen          specimen this interval applies to (nullable = any)
 * @param condition         FHIR qualifiedInterval.condition / category (e.g. "reference")
 * @param artifactId        ZIBO artifact id (provenance)
 * @param version           artifact version (provenance — recorded in trace)
 * @param canonicalUrl      artifact canonical URL (provenance)
 */
public record QualifiedInterval(
        BigDecimal low,
        BigDecimal high,
        String unit,
        BigDecimal criticalLow,
        BigDecimal criticalHigh,
        Sex sex,
        Double ageLowYears,
        Double ageHighYears,
        boolean pregnancy,
        Integer gestationLowWeeks,
        Integer gestationHighWeeks,
        String specimen,
        String condition,
        String artifactId,
        String version,
        String canonicalUrl
) {
    public QualifiedInterval {
        if (sex == null) {
            sex = Sex.UNKNOWN;
        }
    }
}
