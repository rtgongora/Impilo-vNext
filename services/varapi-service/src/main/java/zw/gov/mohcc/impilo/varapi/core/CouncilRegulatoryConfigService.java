package zw.gov.mohcc.impilo.varapi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.integration.CouncilRegulatoryPolicyClient;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilRegulatoryConfigEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRegulatoryConfigRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;

import java.util.Optional;
import java.util.UUID;

@Service
public class CouncilRegulatoryConfigService {

    private final CouncilRegulatoryConfigRepository configRepository;
    private final CouncilRepository councilRepository;
    private final CouncilRegulatoryPolicyClient policyClient;

    public CouncilRegulatoryConfigService(
            CouncilRegulatoryConfigRepository configRepository,
            CouncilRepository councilRepository,
            CouncilRegulatoryPolicyClient policyClient) {
        this.configRepository = configRepository;
        this.councilRepository = councilRepository;
        this.policyClient = policyClient;
    }

    @Transactional(readOnly = true)
    public Optional<CouncilRegulatoryConfigEntity> getForCouncil(Long councilId) {
        TrustContext ctx = TrustContextHolder.require();
        requireTenant(ctx);
        councilRepository.findByIdAndTenantId(councilId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Council not found: " + councilId));
        return configRepository.findByTenantIdAndCouncil_Id(ctx.tenantId(), councilId);
    }

    @Transactional
    public CouncilRegulatoryConfigEntity upsertForCouncil(
            Long councilId,
            Integer configVersion,
            String feesJson,
            String cpdRulesJson,
            String workflowHintsJson,
            String documentRequirementsJson,
            String workflowTemplateCode,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        requireTenant(ctx);
        if (!policyClient.isAllowed("UPSERT_COUNCIL_REGULATORY_CONFIG", councilId, null, null)) {
            throw new IllegalStateException("POLICY_DENIED");
        }
        CouncilEntity council = councilRepository.findByIdAndTenantId(councilId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Council not found: " + councilId));

        CouncilRegulatoryConfigEntity row = configRepository
                .findByTenantIdAndCouncil_Id(ctx.tenantId(), councilId)
                .orElseGet(CouncilRegulatoryConfigEntity::new);
        row.setTenantId(ctx.tenantId());
        row.setCouncil(council);
        if (configVersion != null) {
            row.setConfigVersion(configVersion);
        }
        if (feesJson != null) {
            row.setFeesJson(feesJson);
        }
        if (cpdRulesJson != null) {
            row.setCpdRulesJson(cpdRulesJson);
        }
        if (workflowHintsJson != null) {
            row.setWorkflowHintsJson(workflowHintsJson);
        }
        if (documentRequirementsJson != null) {
            row.setDocumentRequirementsJson(documentRequirementsJson);
        }
        if (workflowTemplateCode != null) {
            row.setWorkflowTemplateCode(workflowTemplateCode);
        }
        if (notes != null) {
            row.setNotes(notes);
        }
        return configRepository.save(row);
    }

    private static void requireTenant(TrustContext ctx) {
        if (ctx.tenantId() == null) {
            throw new IllegalStateException("Tenant is required");
        }
    }
}
