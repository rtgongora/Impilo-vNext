package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.authz.dto.PolicyRuleRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.PolicyRuleResponse;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyRuleEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.PolicyRuleRepository;

import java.util.List;
import java.util.UUID;

/**
 * CRUD service for policy rule management.
 *
 * <p>Invalidates the Redis policy cache on every write operation.</p>
 */
@Service
public class PolicyManagementService {

    private static final Logger log = LoggerFactory.getLogger(PolicyManagementService.class);

    private final PolicyRuleRepository ruleRepository;
    private final PolicyCacheService cacheService;

    public PolicyManagementService(PolicyRuleRepository ruleRepository,
                                   PolicyCacheService cacheService) {
        this.ruleRepository = ruleRepository;
        this.cacheService = cacheService;
    }

    /**
     * List all policy rules for a tenant (including inactive).
     */
    public List<PolicyRuleResponse> listRules(UUID tenantId) {
        return ruleRepository.findByTenantIdOrderByPriorityAsc(tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Create a new policy rule.
     */
    @Transactional
    public PolicyRuleResponse createRule(PolicyRuleRequest request) {
        PolicyRuleEntity entity = new PolicyRuleEntity();
        applyRequest(entity, request);
        entity = ruleRepository.save(entity);

        cacheService.invalidate(request.tenantId());

        log.info("Policy rule created: id={}, name='{}', tenant={}",
                entity.getId(), entity.getName(), entity.getTenantId());

        return toResponse(entity);
    }

    /**
     * Update an existing policy rule.
     */
    @Transactional
    public PolicyRuleResponse updateRule(Long id, PolicyRuleRequest request) {
        PolicyRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Policy rule not found: " + id));

        if (!entity.getTenantId().equals(request.tenantId())) {
            throw new SecurityException("Tenant mismatch for policy rule " + id);
        }

        applyRequest(entity, request);
        entity = ruleRepository.save(entity);

        cacheService.invalidate(request.tenantId());

        log.info("Policy rule updated: id={}, name='{}', tenant={}",
                entity.getId(), entity.getName(), entity.getTenantId());

        return toResponse(entity);
    }

    /**
     * Delete (deactivate) a policy rule.
     */
    @Transactional
    public void deleteRule(Long id, UUID tenantId) {
        PolicyRuleEntity entity = ruleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Policy rule not found: " + id));

        if (!entity.getTenantId().equals(tenantId)) {
            throw new SecurityException("Tenant mismatch for policy rule " + id);
        }

        entity.setActive(false);
        ruleRepository.save(entity);

        cacheService.invalidate(tenantId);

        log.info("Policy rule deactivated: id={}, tenant={}", id, tenantId);
    }

    private void applyRequest(PolicyRuleEntity entity, PolicyRuleRequest request) {
        entity.setTenantId(request.tenantId());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setActorType(request.actorType());
        entity.setRole(request.role());
        entity.setResourceType(request.resourceType());
        entity.setAction(request.action());
        entity.setPurpose(request.purpose());
        entity.setFacilityScope(request.facilityScope());
        entity.setWorkspaceScope(request.workspaceScope());
        entity.setEffect(request.effect());
        entity.setPriority(request.priority());
        entity.setConditions(request.conditions());
        entity.setActive(request.active());
    }

    private PolicyRuleResponse toResponse(PolicyRuleEntity entity) {
        return new PolicyRuleResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getDescription(),
                entity.getActorType(),
                entity.getRole(),
                entity.getResourceType(),
                entity.getAction(),
                entity.getPurpose(),
                entity.isFacilityScope(),
                entity.isWorkspaceScope(),
                entity.getEffect(),
                entity.getPriority(),
                entity.getConditions(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
