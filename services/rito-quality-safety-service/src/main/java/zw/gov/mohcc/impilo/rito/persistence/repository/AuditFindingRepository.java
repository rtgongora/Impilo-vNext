package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.AuditFindingEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditFindingRepository extends JpaRepository<AuditFindingEntity, UUID> {

    List<AuditFindingEntity> findByAuditId(UUID auditId);

    List<AuditFindingEntity> findByTenantIdAndFindingType(UUID tenantId, String findingType);
}
