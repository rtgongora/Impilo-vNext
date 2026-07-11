package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InspectionTypeReferenceEntity;

public interface InspectionTypeReferenceRepository extends JpaRepository<InspectionTypeReferenceEntity, String> {
}
