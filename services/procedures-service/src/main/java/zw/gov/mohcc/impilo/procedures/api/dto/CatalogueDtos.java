package zw.gov.mohcc.impilo.procedures.api.dto;

import java.util.List;

/** Read-side shapes for the procedure catalogue. */
public final class CatalogueDtos {

    private CatalogueDtos() {
    }

    /**
     * A catalogue listing.
     *
     * <p>{@code catalogueSize} is deliberately part of the contract. It reports how many
     * published definitions exist irrespective of the filter, so a caller can distinguish a
     * filter that matched nothing from a catalogue that is empty. A specialty menu that
     * renders nothing because the catalogue did not load silently removes capability from a
     * clinician, and an empty array alone cannot tell those apart.</p>
     */
    public record CatalogueListResponse(List<CatalogueSummary> items, int matched, long catalogueSize) {
    }

    public record CatalogueSummary(
            String definitionCode,
            int version,
            String clinicalName,
            String category,
            String owningSpecialty,
            String purpose,
            List<String> permittedSettings,
            String lateralityApplicability,
            boolean requiresSiteSideVerification,
            Integer expectedDurationMin,
            String ziboCode) {
    }

    public record CatalogueDetail(
            String definitionCode,
            int version,
            String clinicalName,
            List<String> synonyms,
            String category,
            String owningSpecialty,
            String purpose,
            List<String> permittedSettings,
            String anatomicalSite,
            String lateralityApplicability,
            List<String> techniqueVariants,
            Integer ageMinDays,
            Integer ageMaxDays,
            String pregnancyApplicability,
            String indicationRuleRef,
            String contraindicationRuleRef,
            Integer expectedDurationMin,
            boolean recoveryRequired,
            Integer observationMinutes,
            String consentType,
            String safetyPauseTemplate,
            String findingsTemplate,
            String aftercareTemplate,
            String complicationProfile,
            String followUpPolicy,
            String ziboCode,
            String snomedCtCode,
            String icd9cmProcedureCode,
            String status,
            String approvingAuthority,
            String sourceCitation,
            List<RequirementView> requirements) {
    }

    public record RequirementView(
            String requirementKind,
            String requirementCode,
            String requirementLabel,
            String obligation,
            String conditionExpression,
            String ownerRole,
            String resolverService,
            boolean overridableInEmergency,
            String onResolverUnavailable) {
    }
}
