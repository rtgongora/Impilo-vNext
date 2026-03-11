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

    @Query("SELECT s FROM SiteEntity s WHERE s.updatedAt <= :asOf ORDER BY s.siteId")
    Page<SiteEntity> findSnapshotAsOf(@Param("asOf") OffsetDateTime asOf, Pageable pageable);
}
