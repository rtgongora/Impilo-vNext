package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderAuthorizationLinkEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderAuthorizationLinkRepository extends JpaRepository<ProviderAuthorizationLinkEntity, UUID> {

    Optional<ProviderAuthorizationLinkEntity> findFirstByTenantIdAndProviderIdAndStatus(
            UUID tenantId, Long providerId, String status);

    List<ProviderAuthorizationLinkEntity> findByTenantIdAndProviderIdOrderByBoundAtDesc(
            UUID tenantId, Long providerId);

    List<ProviderAuthorizationLinkEntity> findByTenantIdAndImpiloHealthIdAndStatus(
            UUID tenantId, UUID impiloHealthId, String status);

    /**
     * Supersede every ACTIVE link for a provider ahead of a rebind. Runs in the
     * caller's transaction so supersede + new ACTIVE row commit atomically.
     */
    @Modifying
    @Query("UPDATE ProviderAuthorizationLinkEntity l"
            + " SET l.status = 'SUPERSEDED', l.supersededAt = :now"
            + " WHERE l.tenantId = :tenantId AND l.providerId = :providerId AND l.status = 'ACTIVE'")
    int supersedeActiveForProvider(@Param("tenantId") UUID tenantId,
                                   @Param("providerId") Long providerId,
                                   @Param("now") Instant now);
}
