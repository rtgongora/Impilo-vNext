package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityRegulatoryProfileEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityRegulatoryProfileRepository extends JpaRepository<FacilityRegulatoryProfileEntity, UUID> {

    Optional<FacilityRegulatoryProfileEntity> findByFacilityId(Long facilityId);

    List<FacilityRegulatoryProfileEntity> findByClassificationStatus(String classificationStatus);

    @Query("""
            SELECT p FROM FacilityRegulatoryProfileEntity p
            WHERE (:status IS NULL OR p.classificationStatus = :status)
              AND (:hpaClass IS NULL OR p.hpaClass = :hpaClass)
              AND (:canonicalType IS NULL OR p.canonicalType = :canonicalType)
              AND (:sourceType IS NULL OR p.sourceFacilityType = :sourceType)
            ORDER BY p.updatedAt DESC
            """)
    Page<FacilityRegulatoryProfileEntity> search(@Param("status") String status,
                                                 @Param("hpaClass") String hpaClass,
                                                 @Param("canonicalType") String canonicalType,
                                                 @Param("sourceType") String sourceType,
                                                 Pageable pageable);

    @Query("SELECT p.classificationStatus AS status, COUNT(p) AS total " +
           "FROM FacilityRegulatoryProfileEntity p GROUP BY p.classificationStatus")
    List<Object[]> countByStatus();

    @Query("SELECT p.hpaClass AS hpaClass, COUNT(p) AS total " +
           "FROM FacilityRegulatoryProfileEntity p GROUP BY p.hpaClass")
    List<Object[]> countByHpaClass();
}
