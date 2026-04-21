package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityDocumentEntity;

import java.util.List;
import java.util.UUID;

public interface FacilityDocumentRepository extends JpaRepository<FacilityDocumentEntity, UUID> {
    List<FacilityDocumentEntity> findByFacilityIdOrderByUploadedAtDesc(Long facilityId);
    List<FacilityDocumentEntity> findByApplicationApplicationIdOrderByUploadedAtDesc(UUID applicationId);
}
