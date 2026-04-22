package zw.gov.mohcc.impilo.governance.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.domain.GovernanceEnums;
import zw.gov.mohcc.impilo.governance.persistence.RoleDefinitionEntity;
import zw.gov.mohcc.impilo.governance.persistence.RoleDefinitionRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.UUID;

@Service
public class RoleCatalogService {

    private final RoleDefinitionRepository roleDefinitionRepository;
    private final GovernanceEventService governanceEventService;
    private final ObjectMapper objectMapper;

    public RoleCatalogService(RoleDefinitionRepository roleDefinitionRepository,
                              GovernanceEventService governanceEventService,
                              ObjectMapper objectMapper) {
        this.roleDefinitionRepository = roleDefinitionRepository;
        this.governanceEventService = governanceEventService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RoleDefinitionEntity createRoleDefinition(UUID tenantId,
                                                     String roleCode,
                                                     String name,
                                                     String description,
                                                     GovernanceEnums.RoleCategory category,
                                                     GovernanceEnums.RoleLevel level,
                                                     Iterable<GovernanceEnums.AssignmentTargetType> allowedTargets,
                                                     boolean requiresProvider,
                                                     boolean requiresProfessionalStanding,
                                                     boolean specialGovernance) {
        TrustContext ctx = TrustContextHolder.require();
        if (roleDefinitionRepository.findByTenantIdAndRoleCode(tenantId, roleCode).isPresent()) {
            throw new IllegalArgumentException("Role code already exists");
        }
        ArrayNode arr = objectMapper.createArrayNode();
        for (GovernanceEnums.AssignmentTargetType t : allowedTargets) {
            arr.add(t.name());
        }
        RoleDefinitionEntity r = new RoleDefinitionEntity(
                UUID.randomUUID(),
                tenantId,
                roleCode,
                name,
                category.name(),
                level.name(),
                arr.toString());
        r.setDescription(description);
        r.setRequiresProviderFlag(requiresProvider);
        r.setRequiresProfessionalStandingFlag(requiresProfessionalStanding);
        r.setSpecialGovernanceFlag(specialGovernance);
        r = roleDefinitionRepository.save(r);
        governanceEventService.enqueue("ROLE_DEFINITION", r.getId().toString(), "impilo.governance.role_definition.created",
                Map.of("roleDefinitionId", r.getId().toString(), "roleCode", roleCode),
                tenantId, ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        return r;
    }
}
