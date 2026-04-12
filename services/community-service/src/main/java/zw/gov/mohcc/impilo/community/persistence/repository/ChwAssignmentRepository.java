package zw.gov.mohcc.impilo.community.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.community.persistence.entity.ChwAssignmentEntity;

import java.util.List;
import java.util.UUID;

public interface ChwAssignmentRepository extends JpaRepository<ChwAssignmentEntity, Long> {

    List<ChwAssignmentEntity> findByTenantIdOrderByAssignedAtDesc(UUID tenantId);

    List<ChwAssignmentEntity> findByUnitIdOrderByAssignedAtDesc(UUID unitId);
}
