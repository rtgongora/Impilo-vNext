package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ClassificationTemplateApplicabilityEntity;

import java.util.List;
import java.util.UUID;

public interface ClassificationTemplateApplicabilityRepository extends JpaRepository<ClassificationTemplateApplicabilityEntity, UUID> {

    List<ClassificationTemplateApplicabilityEntity> findByClassificationLabelIgnoreCase(String classificationLabel);

    List<ClassificationTemplateApplicabilityEntity> findByFacilityTypeIgnoreCase(String facilityType);

    List<ClassificationTemplateApplicabilityEntity> findByUnitTypeIgnoreCase(String unitType);
}
