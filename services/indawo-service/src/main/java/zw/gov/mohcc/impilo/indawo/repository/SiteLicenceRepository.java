package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.indawo.domain.SiteLicenceEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SiteLicenceRepository extends JpaRepository<SiteLicenceEntity, UUID> {
    List<SiteLicenceEntity> findBySiteIdOrderByExpiryDateDesc(UUID siteId);

    @Query("""
            SELECT l
            FROM SiteLicenceEntity l
            WHERE l.tenantId = :tenantId
              AND l.status = 'ACTIVE'
              AND l.expiryDate <= :asOf
            """)
    List<SiteLicenceEntity> findActiveExpiringBy(@Param("tenantId") UUID tenantId, @Param("asOf") LocalDate asOf);
}

