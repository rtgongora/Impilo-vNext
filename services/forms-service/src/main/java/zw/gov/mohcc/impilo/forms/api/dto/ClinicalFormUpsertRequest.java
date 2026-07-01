package zw.gov.mohcc.impilo.forms.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Rich upsert for an encounter clinical form definition (WHO-DAK aligned).
 * Used by the clinical-form admin endpoint and the seed loader. Idempotent by (tenant, formKey):
 * creates the schema + v1 on first sight, adds a new immutable version when {@code definition} changes.
 */
public record ClinicalFormUpsertRequest(
        @NotBlank String formKey,
        @NotBlank String name,
        String description,
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
        JsonNode eligibility,
        JsonNode terminologyBindings,
        JsonNode resourceMappings,
        JsonNode indicators,
        Boolean requiresCountersign,
        Boolean offlineCapable,
        String sensitivity,
        String governanceAuthor,
        String governanceReviewer,
        String governanceApprover,
        String sourceGuideline,
        String changeReason,
        Boolean publish,
        /** The full WHO-DAK ClinicalFormDefinition JSON (stored immutably in the version snapshot). */
        @NotBlank String definition
) {
}
