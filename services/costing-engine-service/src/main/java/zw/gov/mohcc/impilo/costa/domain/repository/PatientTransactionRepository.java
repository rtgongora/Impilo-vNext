package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.PatientTransactionEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PatientTransactionRepository extends JpaRepository<PatientTransactionEntity, Long> {

    List<PatientTransactionEntity> findByTenantIdAndTxnTypeOrderByTxnDateDesc(UUID tenantId, String txnType);

    List<PatientTransactionEntity> findByTenantIdAndFacilityIdOrderByTxnDateDesc(UUID tenantId, UUID facilityId);

    @Query("""
            SELECT t FROM PatientTransactionEntity t
            WHERE t.tenantId = :tenantId AND t.patientCpid = :patientCpid
            AND (:from IS NULL OR t.txnDate >= :from)
            AND (:to IS NULL OR t.txnDate <= :to)
            ORDER BY t.txnDate DESC, t.id DESC
            """)
    Page<PatientTransactionEntity> findForPatient(
            @Param("tenantId") UUID tenantId,
            @Param("patientCpid") String patientCpid,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            Pageable pageable);
}
