package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityDepartmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityDepartmentRepository extends JpaRepository<FacilityDepartmentEntity, UUID> {
    List<FacilityDepartmentEntity> findByFacilityIdAndActiveTrueOrderByOfficialNameAsc(Long facilityId);
    Optional<FacilityDepartmentEntity> findByFacilityIdAndDepartmentCodeAndActiveTrue(Long facilityId, String departmentCode);
    long countByFacilityIdAndActiveTrue(Long facilityId);
}
