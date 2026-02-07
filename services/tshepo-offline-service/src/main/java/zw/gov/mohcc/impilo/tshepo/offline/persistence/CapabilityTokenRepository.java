package zw.gov.mohcc.impilo.tshepo.offline.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CapabilityTokenRepository extends JpaRepository<CapabilityTokenEntity, UUID> {

    @Query("SELECT c FROM CapabilityTokenEntity c WHERE c.id = :id AND c.tenantId = :tenantId")
    Optional<CapabilityTokenEntity> findByIdAndTenantId(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId);

    @Query("SELECT c FROM CapabilityTokenEntity c WHERE c.tenantId = :tenantId AND c.actorId = :actorId " +
           "AND c.status = 'ACTIVE' ORDER BY c.issuedAt DESC")
    List<CapabilityTokenEntity> findActiveByTenantAndActor(
            @Param("tenantId") UUID tenantId,
            @Param("actorId") String actorId);

    @Query("SELECT c FROM CapabilityTokenEntity c WHERE c.tenantId = :tenantId AND c.facilityId = :facilityId " +
           "AND c.status = 'ACTIVE' ORDER BY c.issuedAt DESC")
    List<CapabilityTokenEntity> findActiveByTenantAndFacility(
            @Param("tenantId") UUID tenantId,
            @Param("facilityId") UUID facilityId);

    @Query("SELECT c FROM CapabilityTokenEntity c WHERE c.status = 'ACTIVE' AND c.expiresAt < CURRENT_TIMESTAMP")
    List<CapabilityTokenEntity> findExpiredActiveTokens();
}
