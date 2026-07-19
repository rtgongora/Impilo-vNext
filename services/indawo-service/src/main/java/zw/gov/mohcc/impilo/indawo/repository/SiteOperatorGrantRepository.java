package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.indawo.domain.SiteOperatorGrantEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteOperatorGrantRepository extends JpaRepository<SiteOperatorGrantEntity, Long> {

    Optional<SiteOperatorGrantEntity> findByIdAndTenantId(Long id, UUID tenantId);

    List<SiteOperatorGrantEntity> findByTenantIdAndSiteIdOrderByCreatedAtDesc(UUID tenantId, UUID siteId);

    List<SiteOperatorGrantEntity> findByTenantIdAndApprovalStateOrderByCreatedAtDesc(
            UUID tenantId, String approvalState);

    boolean existsByTenantIdAndSiteIdAndPersonHealthIdAndRoleAndApprovalState(
            UUID tenantId, UUID siteId, String personHealthId, String role, String approvalState);

    /** Expiry sweep feed: ACTIVE grants whose validity window has passed. */
    List<SiteOperatorGrantEntity> findByApprovalStateAndValidToBefore(String approvalState, LocalDate validTo);
}
