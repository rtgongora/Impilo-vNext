package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityAuditEventEntity;

import java.util.List;
import java.util.UUID;

public interface FacilityAuditEventRepository extends JpaRepository<FacilityAuditEventEntity, UUID> {
    List<FacilityAuditEventEntity> findByFacilityIdOrderByCreatedAtDesc(Long facilityId);
}
