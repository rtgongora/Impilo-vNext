package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionTemplateCompositionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionTemplateCompositionRepository extends JpaRepository<InspectionTemplateCompositionEntity, UUID> {

    Optional<InspectionTemplateCompositionEntity> findByCodeAndVersion(String code, Integer version);

    Optional<InspectionTemplateCompositionEntity> findFirstByCodeAndStatusOrderByVersionDesc(String code, String status);

    List<InspectionTemplateCompositionEntity> findAllByOrderByCodeAscVersionDesc();
}
