package zw.gov.mohcc.impilo.tshepo.offline.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReconciliationBatchRepository extends JpaRepository<ReconciliationBatchEntity, UUID> {

    @Query("SELECT r FROM ReconciliationBatchEntity r WHERE r.id = :id AND r.tenantId = :tenantId")
    Optional<ReconciliationBatchEntity> findByIdAndTenantId(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId);

    @Query("SELECT r FROM ReconciliationBatchEntity r WHERE r.tenantId = :tenantId " +
           "AND r.status IN ('SUBMITTED', 'PROCESSING') ORDER BY r.submittedAt ASC")
    List<ReconciliationBatchEntity> findPendingByTenant(
            @Param("tenantId") UUID tenantId);

    @Query("SELECT r FROM ReconciliationBatchEntity r WHERE r.tenantId = :tenantId " +
           "AND r.facilityId = :facilityId ORDER BY r.submittedAt DESC")
    List<ReconciliationBatchEntity> findByTenantAndFacility(
            @Param("tenantId") UUID tenantId,
            @Param("facilityId") UUID facilityId);
}
