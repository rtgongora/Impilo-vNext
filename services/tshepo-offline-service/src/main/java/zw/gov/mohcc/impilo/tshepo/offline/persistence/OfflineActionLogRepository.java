package zw.gov.mohcc.impilo.tshepo.offline.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OfflineActionLogRepository extends JpaRepository<OfflineActionLogEntity, Long> {

    @Query("SELECT a FROM OfflineActionLogEntity a WHERE a.tenantId = :tenantId " +
           "AND a.capabilityTokenId = :tokenId ORDER BY a.performedAt ASC")
    List<OfflineActionLogEntity> findByTenantAndToken(
            @Param("tenantId") UUID tenantId,
            @Param("tokenId") UUID tokenId);

    @Query("SELECT a FROM OfflineActionLogEntity a WHERE a.tenantId = :tenantId " +
           "AND a.syncStatus = :syncStatus ORDER BY a.performedAt ASC")
    List<OfflineActionLogEntity> findByTenantAndSyncStatus(
            @Param("tenantId") UUID tenantId,
            @Param("syncStatus") String syncStatus);

    @Query("SELECT a FROM OfflineActionLogEntity a WHERE a.tenantId = :tenantId " +
           "AND a.actorId = :actorId ORDER BY a.performedAt DESC")
    Page<OfflineActionLogEntity> findByTenantAndActor(
            @Param("tenantId") UUID tenantId,
            @Param("actorId") String actorId,
            Pageable pageable);

    @Query("SELECT a FROM OfflineActionLogEntity a WHERE a.tenantId = :tenantId " +
           "AND a.syncStatus = 'PENDING' ORDER BY a.performedAt ASC")
    List<OfflineActionLogEntity> findPendingByTenant(
            @Param("tenantId") UUID tenantId);

    @Query("SELECT COUNT(a) FROM OfflineActionLogEntity a WHERE a.capabilityTokenId = :tokenId " +
           "AND a.actionType = 'CREATE_PROVISIONAL_ENCOUNTER'")
    long countProvisionalEncountersByToken(@Param("tokenId") UUID tokenId);
}
