package zw.gov.mohcc.impilo.tshepo.offline.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OfflinePackRepository extends JpaRepository<OfflinePackEntity, UUID> {

    @Query("SELECT p FROM OfflinePackEntity p WHERE p.id = :id AND p.tenantId = :tenantId")
    Optional<OfflinePackEntity> findByIdAndTenantId(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId);

    @Query("SELECT p FROM OfflinePackEntity p WHERE p.tenantId = :tenantId AND p.facilityId = :facilityId " +
           "ORDER BY p.generatedAt DESC LIMIT 1")
    Optional<OfflinePackEntity> findLatestByTenantAndFacility(
            @Param("tenantId") UUID tenantId,
            @Param("facilityId") UUID facilityId);

    @Query("SELECT COALESCE(MAX(p.version), 0) FROM OfflinePackEntity p " +
           "WHERE p.tenantId = :tenantId AND p.facilityId = :facilityId")
    int findMaxVersionByTenantAndFacility(
            @Param("tenantId") UUID tenantId,
            @Param("facilityId") UUID facilityId);
}
