package zw.gov.mohcc.impilo.tshepo.authz.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.PolicyRuleEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface PolicyRuleRepository extends JpaRepository<PolicyRuleEntity, Long> {

    /**
     * Find all active policy rules for a tenant, ordered by priority (DENY first).
     */
    @Query("SELECT r FROM PolicyRuleEntity r WHERE r.tenantId = :tenantId AND r.active = true ORDER BY r.effect DESC, r.priority ASC")
    List<PolicyRuleEntity> findByTenantIdAndActiveTrue(@Param("tenantId") UUID tenantId);

    /**
     * Find active policy rules for a tenant and resource type, ordered by priority.
     */
    @Query("SELECT r FROM PolicyRuleEntity r WHERE r.tenantId = :tenantId AND r.resourceType = :resourceType AND r.active = true ORDER BY r.effect DESC, r.priority ASC")
    List<PolicyRuleEntity> findByTenantIdAndResourceTypeAndActiveTrue(
            @Param("tenantId") UUID tenantId,
            @Param("resourceType") String resourceType);

    /**
     * Find all rules for a tenant (including inactive), for admin listing.
     */
    List<PolicyRuleEntity> findByTenantIdOrderByPriorityAsc(UUID tenantId);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndActiveTrue(UUID tenantId);

    /** Feeds the security-readiness report (Phase D, D6): named-prefix rule families. */
    long countByTenantIdAndNameStartingWithAndActiveTrue(UUID tenantId, String namePrefix);

    long countByTenantIdAndNameStartingWith(UUID tenantId, String namePrefix);
}
