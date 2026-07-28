package zw.gov.mohcc.impilo.procedures.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.procedures.api.dto.CatalogueDtos.*;
import zw.gov.mohcc.impilo.procedures.persistence.entity.ProcedureDefinitionEntity;
import zw.gov.mohcc.impilo.procedures.persistence.entity.ProcedureRequirementEntity;
import zw.gov.mohcc.impilo.procedures.persistence.repository.ProcedureDefinitionRepository;
import zw.gov.mohcc.impilo.procedures.persistence.repository.ProcedureRequirementRepository;

import java.util.List;
import java.util.UUID;

/**
 * Read access to the canonical procedure catalogue.
 *
 * <p>Only the PUBLISHED version of a definition is ever served. A draft is content in
 * progress and a retired one describes a procedure the service no longer governs; serving
 * either would let a procedure be judged against a standard nobody approved.</p>
 */
@Service
public class ProcedureCatalogueService {

    private static final String PUBLISHED = "PUBLISHED";
    private static final String SITE_SIDE = "SITE_SIDE_VERIFICATION";

    private final ProcedureDefinitionRepository definitions;
    private final ProcedureRequirementRepository requirements;

    public ProcedureCatalogueService(ProcedureDefinitionRepository definitions,
                                     ProcedureRequirementRepository requirements) {
        this.definitions = definitions;
        this.requirements = requirements;
    }

    @Transactional(readOnly = true)
    public CatalogueListResponse search(UUID tenantId, String specialty, String category, String query) {
        List<ProcedureDefinitionEntity> found =
                definitions.search(tenantId, blankToNull(specialty), blankToNull(category), blankToNull(query));
        List<CatalogueSummary> items = found.stream().map(this::toSummary).toList();
        // catalogueSize is reported alongside the match count so a caller can tell an empty
        // result from an empty catalogue. See CatalogueListResponse.
        return new CatalogueListResponse(items, items.size(), definitions.countPublished(tenantId));
    }

    @Transactional(readOnly = true)
    public CatalogueDetail byCode(UUID tenantId, String definitionCode) {
        ProcedureDefinitionEntity d = definitions
                .findByTenantIdAndDefinitionCodeAndStatus(tenantId, definitionCode, PUBLISHED)
                .orElseThrow(() -> new ProceduresDomainException(
                        "CATALOGUE_ENTRY_NOT_FOUND", 404,
                        "No published catalogue entry for '" + definitionCode + "'"));
        return toDetail(d, requirements.findByDefinitionIdOrderByDisplayOrderAsc(d.getId()));
    }

    private CatalogueSummary toSummary(ProcedureDefinitionEntity d) {
        // Surfaced on the summary rather than only the detail, because a caller building a
        // request needs to know it must collect a side BEFORE it opens the detail.
        boolean siteSide = requirements.findByDefinitionIdOrderByDisplayOrderAsc(d.getId()).stream()
                .anyMatch(r -> SITE_SIDE.equals(r.getRequirementKind()));
        return new CatalogueSummary(
                d.getDefinitionCode(), d.getVersion(), d.getClinicalName(), d.getCategory(),
                d.getOwningSpecialty(), d.getPurpose(), d.getPermittedSettings(),
                d.getLateralityApplicability(), siteSide, d.getExpectedDurationMin(), d.getZiboCode());
    }

    private CatalogueDetail toDetail(ProcedureDefinitionEntity d, List<ProcedureRequirementEntity> reqs) {
        return new CatalogueDetail(
                d.getDefinitionCode(), d.getVersion(), d.getClinicalName(), d.getSynonyms(),
                d.getCategory(), d.getOwningSpecialty(), d.getPurpose(), d.getPermittedSettings(),
                d.getAnatomicalSite(), d.getLateralityApplicability(), d.getTechniqueVariants(),
                d.getAgeMinDays(), d.getAgeMaxDays(), d.getPregnancyApplicability(),
                d.getIndicationRuleRef(), d.getContraindicationRuleRef(),
                d.getExpectedDurationMin(), d.isRecoveryRequired(), d.getObservationMinutes(),
                d.getConsentType(), d.getSafetyPauseTemplate(), d.getFindingsTemplate(),
                d.getAftercareTemplate(),
                d.getDefaultSedationLevelCode(), d.getDefaultRecoverySettingCode(),
                d.getDefaultAftercareTemplateCode(),
                d.getComplicationProfile(), d.getFollowUpPolicy(),
                d.getZiboCode(), d.getSnomedCtCode(), d.getIcd9cmProcedureCode(),
                d.getStatus(), d.getApprovingAuthority(), d.getSourceCitation(),
                reqs.stream().map(r -> new RequirementView(
                        r.getRequirementKind(), r.getRequirementCode(), r.getRequirementLabel(),
                        r.getObligation(), r.getConditionExpression(), r.getOwnerRole(),
                        r.getResolverService(), r.isOverridableInEmergency(),
                        r.getOnResolverUnavailable())).toList());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
