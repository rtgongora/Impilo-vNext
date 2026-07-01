package zw.gov.mohcc.impilo.pct.core.forms;

import java.util.List;

/**
 * Output of the {@link FormScopeEngine}: the encounter form obligation set.
 * Buckets are mutually exclusive. {@code auditRef} is stamped by {@code FormResolverService}.
 */
public record FormResolution(
        List<FormObligation> mandatory,
        List<FormObligation> recommended,
        List<FormObligation> optional,
        List<FormObligation> prohibited,
        List<FormObligation> countersignRequired,
        String auditRef) {

    /** One resolved form with its obligation verdict and (for prohibited/advisory) the reason. */
    public record FormObligation(
            String formKey,
            String formSchemaId,
            int formVersion,
            String formSchemaVersionId,
            String name,
            String obligation,          // MANDATORY | RECOMMENDED | OPTIONAL | PROHIBITED | COUNTERSIGN_REQUIRED
            String requiredWorkflow,
            boolean requiresCountersign,
            // Advisory only: the cadre may perform this workflow but the CadreEngine flags it as
            // escalation-worthy (e.g. a nurse prescribing). This is NOT a hard countersign gate — it is a
            // hint for UI nudges + audit + future prescribing policy. Countersignature is enforced only when
            // requiresCountersign is set by the form author (or a future prescribing policy rule).
            boolean supervisorRecommended,
            String reason) {}
}
