package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.assetregistry.domain.FaultReportEntity;

import java.util.List;
import java.util.UUID;

public interface FaultReportRepository extends JpaRepository<FaultReportEntity, UUID> {
    List<FaultReportEntity> findByEquipmentIdOrderByReportedAtDesc(UUID equipmentId);
    List<FaultReportEntity> findByTenantIdAndStatusOrderByReportedAtDesc(UUID tenantId, String status);
}
