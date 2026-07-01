package zw.gov.mohcc.impilo.pct.core.forms;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * PCT-side view of a clinical form definition, fetched from forms-service {@code /internal/v1/forms/catalog}.
 * Carries the queryable applicability metadata + immutable version token + full WHO-DAK definition JSON.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
        String definitionJson
) {
    public List<String> careSettingsOrEmpty() { return careSettings == null ? List.of() : careSettings; }
    public List<String> programmesOrEmpty() { return programmeApplicability == null ? List.of() : programmeApplicability; }
}
