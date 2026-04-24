package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.PractitionerInChargeAssignmentEntity;

import java.util.List;

public interface PractitionerInChargeAssignmentRepository extends JpaRepository<PractitionerInChargeAssignmentEntity, Long> {
    List<PractitionerInChargeAssignmentEntity> findByFacilityIdOrderByStartDateDesc(Long facilityId);
    java.util.Optional<PractitionerInChargeAssignmentEntity> findByFacilityIdAndExternalAssignmentId(Long facilityId, Long externalAssignmentId);
}
