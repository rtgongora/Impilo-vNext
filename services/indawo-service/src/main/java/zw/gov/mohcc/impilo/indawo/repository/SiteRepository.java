package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.indawo.domain.SiteEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface SiteRepository extends JpaRepository<SiteEntity, UUID> {

    Page<SiteEntity> findByTenantId(UUID tenantId, Pageable pageable);

    @Query("""
            SELECT s
            FROM SiteEntity s
            WHERE s.tenantId = :tenantId
              AND (:search IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR (s.siteCode IS NOT NULL AND LOWER(s.siteCode) LIKE LOWER(CONCAT('%', :search, '%'))))
              AND (:siteCategory IS NULL OR s.siteCategory = :siteCategory)
              AND (:regulatoryStatus IS NULL OR s.regulatoryStatus = :regulatoryStatus)
              AND (:province IS NULL OR s.province = :province)
              AND (:district IS NULL OR s.district = :district)
            """)
    Page<SiteEntity> searchSites(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            @Param("regulatoryStatus") String regulatoryStatus,
            @Param("siteCategory") String siteCategory,
            @Param("province") String province,
            @Param("district") String district,
            Pageable pageable);

    @Query("SELECT s FROM SiteEntity s WHERE s.updatedAt <= :asOf ORDER BY s.siteId")
    Page<SiteEntity> findSnapshotAsOf(@Param("asOf") OffsetDateTime asOf, Pageable pageable);
}
