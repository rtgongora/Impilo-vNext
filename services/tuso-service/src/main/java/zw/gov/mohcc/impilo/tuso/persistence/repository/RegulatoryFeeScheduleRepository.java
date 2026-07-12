package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryFeeScheduleEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegulatoryFeeScheduleRepository extends JpaRepository<RegulatoryFeeScheduleEntity, UUID> {

    List<RegulatoryFeeScheduleEntity> findByScheduleCodeOrderByFeeCodeAscVersionDesc(String scheduleCode);

    List<RegulatoryFeeScheduleEntity> findByAppliesToCatalogueCodeOrderByVersionDesc(String appliesToCatalogueCode);

    /** ACTIVE row for (catalogue, class) — class-specific wins, else class-independent. Highest version first. */
    @Query("""
            SELECT f FROM RegulatoryFeeScheduleEntity f
            WHERE f.status = 'ACTIVE'
              AND f.appliesToCatalogueCode = :catalogueCode
              AND (f.appliesToClassCode = :classCode OR f.appliesToClassCode IS NULL)
            ORDER BY CASE WHEN f.appliesToClassCode = :classCode THEN 0 ELSE 1 END, f.version DESC
            """)
    List<RegulatoryFeeScheduleEntity> findActiveForResolution(@Param("catalogueCode") String catalogueCode,
                                                              @Param("classCode") String classCode);

    Optional<RegulatoryFeeScheduleEntity> findByScheduleCodeAndFeeCodeAndAppliesToCatalogueCodeAndVersion(
            String scheduleCode, String feeCode, String appliesToCatalogueCode, Integer version);
}
