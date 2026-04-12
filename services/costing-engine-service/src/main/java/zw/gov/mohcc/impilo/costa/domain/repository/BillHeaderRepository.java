package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.BillHeaderEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.BillStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillHeaderRepository extends JpaRepository<BillHeaderEntity, String> {
    Page<BillHeaderEntity> findByTenantIdAndFacilityIdAndStatus(UUID tenantId, UUID facilityId, BillStatus status, Pageable pageable);
    List<BillHeaderEntity> findByEncounterId(String encounterId);
    Optional<BillHeaderEntity> findByMsikaOrderId(String msikaOrderId);
    Page<BillHeaderEntity> findByTenantIdAndStatus(UUID tenantId, BillStatus status, Pageable pageable);
    Page<BillHeaderEntity> findByTenantId(UUID tenantId, Pageable pageable);

    @Query("""
            SELECT b FROM BillHeaderEntity b, EncounterEntity e
            WHERE b.encounterId = e.encounterId
            AND b.tenantId = :tenantId AND e.tenantId = :tenantId
            AND e.patientCpid = :patientCpid AND b.status = :status
            ORDER BY b.finalizedAt DESC NULLS LAST, b.createdAt DESC
            """)
    List<BillHeaderEntity> findByPatientAndStatus(
            @Param("tenantId") UUID tenantId,
            @Param("patientCpid") String patientCpid,
            @Param("status") BillStatus status);
}
