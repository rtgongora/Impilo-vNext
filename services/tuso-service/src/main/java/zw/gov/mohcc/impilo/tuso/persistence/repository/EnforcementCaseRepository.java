package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EnforcementCaseEntity;

import java.util.List;
import java.util.UUID;

public interface EnforcementCaseRepository extends JpaRepository<EnforcementCaseEntity, UUID> {
    List<EnforcementCaseEntity> findByFacilityIdOrderByOpenedAtDesc(Long facilityId);
}
