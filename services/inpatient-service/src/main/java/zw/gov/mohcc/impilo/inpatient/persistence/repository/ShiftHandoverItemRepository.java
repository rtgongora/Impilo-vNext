package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ShiftHandoverItemEntity;

import java.util.List;
import java.util.UUID;

public interface ShiftHandoverItemRepository extends JpaRepository<ShiftHandoverItemEntity, UUID> {
    List<ShiftHandoverItemEntity> findByHandoverIdOrderByAdmissionRefAsc(UUID handoverId);
}
