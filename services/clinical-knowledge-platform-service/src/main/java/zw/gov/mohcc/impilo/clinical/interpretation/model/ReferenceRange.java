package zw.gov.mohcc.impilo.clinical.interpretation.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A resolved reference interval whose bounds have been UCUM-normalised into the observation's own unit,
 * so the {@code InterpretationEngine} can compare directly. Carries provenance for the audit trace.
 *
 * @param low          lower bound in the observation's unit (nullable = unbounded below)
 * @param high         upper bound in the observation's unit (nullable = unbounded above)
 * @param criticalLow  critical-low threshold in the observation's unit (nullable)
 * @param criticalHigh critical-high threshold in the observation's unit (nullable)
 * @param unit         the unit the bounds are expressed in (the observation's unit)
 * @param source       provenance class
 * @param artifactId   governed-artifact id (nullable for LAB_DELIVERED)
 * @param version      governed-artifact version (nullable for LAB_DELIVERED)
 * @param canonicalUrl governed-artifact canonical URL (nullable for LAB_DELIVERED)
 * @param condition    qualifiedInterval condition/category that matched (nullable)
 */
public record ReferenceRange(
        BigDecimal low,
        BigDecimal high,
        BigDecimal criticalLow,
        BigDecimal criticalHigh,
        String unit,
        RangeSource source,
        String artifactId,
        String version,
        String canonicalUrl,
        String condition
) {

    public static ReferenceRange labDelivered(BigDecimal low, BigDecimal high, String unit) {
        return new ReferenceRange(low, high, null, null, unit, RangeSource.LAB_DELIVERED, null, null, null, null);
    }

    /** Compact map for audit/trace and API serialisation (records the selected range id + version). */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("low", low);
        m.put("high", high);
        m.put("critical_low", criticalLow);
        m.put("critical_high", criticalHigh);
        m.put("unit", unit);
        m.put("source", source == null ? null : source.name());
        m.put("artifact_id", artifactId);
        m.put("version", version);
        m.put("canonical_url", canonicalUrl);
        m.put("condition", condition);
        return m;
    }
}
