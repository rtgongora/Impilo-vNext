package zw.gov.mohcc.impilo.vashandi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.ReferenceQuarantineEntity;

import java.util.List;
import java.util.UUID;

public interface ReferenceQuarantineRepository extends JpaRepository<ReferenceQuarantineEntity, UUID> {
    List<ReferenceQuarantineEntity> findByTenantIdAndResolvedFalseOrderByQuarantinedAtDesc(UUID tenantId);
    List<ReferenceQuarantineEntity> findByWorkforceAssignmentId(UUID workforceAssignmentId);
}
