package zw.gov.mohcc.impilo.clinical.interpretation;

import zw.gov.mohcc.impilo.clinical.interpretation.model.QualifiedInterval;

import java.util.List;

/**
 * Supplies governed FHIR {@code ObservationDefinition.qualifiedInterval}s (hosted in ZIBO) for a coded
 * analyte/vital. This is the swappable seam between the {@code RangeResolver} and the source of governed
 * reference intervals: Phase 1 ships a fail-closed empty default; the ZIBO-backed implementation (with
 * cache) supersedes it without touching the resolver.
 *
 * <p>Implementations MUST be fail-closed: return an empty list (never null, never fabricated bounds)
 * when no governed definition exists or the source is unavailable.</p>
 */
public interface ObservationDefinitionProvider {

    /**
     * Return all governed qualified intervals for the given code(s). The resolver filters/ranks the
     * returned intervals against patient context. LOINC is the preferred key; the local code is a
     * fallback. Either may be null.
     *
     * @param loincCode preferred match key (nullable)
     * @param localCode fallback match key (nullable)
     * @return matching intervals, possibly empty; never null
     */
    List<QualifiedInterval> findIntervals(String loincCode, String localCode);
}
