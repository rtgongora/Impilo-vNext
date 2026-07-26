package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicationReconciliationItemEntity;

import java.util.List;
import java.util.UUID;

public interface MedicationReconciliationItemRepository extends JpaRepository<MedicationReconciliationItemEntity, UUID> {

    List<MedicationReconciliationItemEntity> findByReconciliationId(UUID reconciliationId);
}
