package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ShiftHandoverEntity;

import java.util.List;
import java.util.UUID;

public interface ShiftHandoverRepository extends JpaRepository<ShiftHandoverEntity, UUID> {
    List<ShiftHandoverEntity> findByTenantIdAndFacilityIdAndStatusOrderBySubmittedAtDesc(
            UUID tenantId, UUID facilityId, String status);
}
