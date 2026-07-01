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

    /** One resolved form with its obligation verdict and (for prohibited) the reason. */
    public record FormObligation(
            String formKey,
            String formSchemaId,
            int formVersion,
            String formSchemaVersionId,
            String name,
            String obligation,          // MANDATORY | RECOMMENDED | OPTIONAL | PROHIBITED | COUNTERSIGN_REQUIRED
            String requiredWorkflow,
            boolean requiresCountersign,
            String reason) {}
}
