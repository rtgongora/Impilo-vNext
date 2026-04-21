package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityInspectionType;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionChecklistTemplateEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionChecklistTemplateRepository extends JpaRepository<InspectionChecklistTemplateEntity, UUID> {
    Optional<InspectionChecklistTemplateEntity> findByCode(String code);

    @Query("""
            select t from InspectionChecklistTemplateEntity t
            where t.active = true
              and t.inspectionTypeApplicability = :inspectionType
              and (t.facilityTypeApplicability is null or t.facilityTypeApplicability = :facilityType)
            order by
              case when t.facilityTypeApplicability = :facilityType then 0 else 1 end,
              t.code
            """)
    List<InspectionChecklistTemplateEntity> findApplicableTemplates(FacilityInspectionType inspectionType, String facilityType);
}
