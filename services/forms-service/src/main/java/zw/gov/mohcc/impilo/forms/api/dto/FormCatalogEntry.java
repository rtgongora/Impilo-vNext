package zw.gov.mohcc.impilo.forms.api.dto;

import java.util.List;

/**
 * Resolver-facing projection of a clinical encounter form definition.
 *
 * <p>Carries the queryable applicability + governance metadata AND the immutable version token
 * ({@code formSchemaVersionId}) + the full WHO-DAK definition JSON ({@code definitionJson}) of the
 * current version, so the PCT {@code FormScopeEngine} can bucket forms (mandatory / recommended /
 * optional / prohibited / countersign) without a second round-trip.</p>
 */
public record FormCatalogEntry(
        String formSchemaId,
        String formKey,
        String name,
        String description,
        int version,
        String formSchemaVersionId,
        String status,
        String formType,
        List<String> careSettings,
        List<String> careStages,
        List<String> specialties,
        List<String> encounterTypes,
        List<String> encounterContexts,
        String requiredWorkflow,
        String obligationDefault,
        List<String> permittedCadres,
        Integer minAgeMonths,
        Integer maxAgeMonths,
        String sexApplicability,
        Boolean requirePregnant,
        List<String> programmeApplicability,
        List<String> facilityLevels,
        boolean requiresCountersign,
        boolean offlineCapable,
        String sensitivity,
        String definitionJson,
        String resourceMappings
) {
}
