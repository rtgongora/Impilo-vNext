package zw.gov.mohcc.impilo.pct.core.forms;

import zw.gov.mohcc.impilo.pct.core.cadre.CadreDecision;

import java.util.List;

/**
 * Input to the pure {@link FormScopeEngine}. The {@code cadreDecision} is the OUTPUT of
 * {@code CadreEngine.resolve(...)} — composed here, not recomputed — so all Cadre Engine laws
 * (emergency widening, supervisor escalation, step-up) flow through unchanged (registry GAP-4).
 */
public record FormResolutionRequest(
        CadreDecision cadreDecision,
        String careSetting,
        String careStage,
        String specialty,
        String encounterContext,
        Integer acuity,
        PatientFacts patient,
        String providerCadre,
        List<FormCatalogEntry> catalog) {
}
