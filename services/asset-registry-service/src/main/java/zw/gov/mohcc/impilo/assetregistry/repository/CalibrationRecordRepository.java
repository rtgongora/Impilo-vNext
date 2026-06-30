package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.assetregistry.domain.CalibrationRecordEntity;

import java.util.List;
import java.util.UUID;

public interface CalibrationRecordRepository extends JpaRepository<CalibrationRecordEntity, UUID> {
    List<CalibrationRecordEntity> findByEquipmentIdOrderByPerformedOnDesc(UUID equipmentId);
}
