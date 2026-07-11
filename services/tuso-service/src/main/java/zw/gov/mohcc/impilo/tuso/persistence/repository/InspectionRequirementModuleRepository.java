package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionRequirementModuleEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionRequirementModuleRepository extends JpaRepository<InspectionRequirementModuleEntity, UUID> {

    Optional<InspectionRequirementModuleEntity> findByCodeAndVersion(String code, Integer version);

    Optional<InspectionRequirementModuleEntity> findFirstByCodeAndStatusOrderByVersionDesc(String code, String status);

    List<InspectionRequirementModuleEntity> findAllByOrderByCodeAscVersionDesc();

    List<InspectionRequirementModuleEntity> findByStatus(String status);
}
