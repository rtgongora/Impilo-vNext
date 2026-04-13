package zw.gov.mohcc.impilo.procurement.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.procurement.persistence.entity.RequisitionEntity;

import java.util.List;
import java.util.UUID;

public interface RequisitionRepository extends JpaRepository<RequisitionEntity, UUID> {
    List<RequisitionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
