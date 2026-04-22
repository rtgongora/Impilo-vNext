package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.indawo.domain.SiteAssignmentEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface SiteAssignmentRepository extends JpaRepository<SiteAssignmentEntity, UUID> {
    List<SiteAssignmentEntity> findBySiteIdOrderByUpdatedAtDesc(UUID siteId);
    long countByTenantIdAndStatus(UUID tenantId, String status);
}

