package zw.gov.mohcc.impilo.auditledger.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.auditledger.domain.AuditRecordEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditRecordRepository extends JpaRepository<AuditRecordEntity, Long> {
    Optional<AuditRecordEntity> findByRecordId(UUID recordId);

    List<AuditRecordEntity> findByCorrelationId(UUID correlationId);

    @Query("SELECT r FROM AuditRecordEntity r WHERE r.tenantId = :tenantId ORDER BY r.sequenceNum ASC")
    Page<AuditRecordEntity> findByTenantOrdered(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT r FROM AuditRecordEntity r WHERE r.tenantId = :tenantId"
            + " AND r.sequenceNum >= :fromSeq AND r.sequenceNum <= :toSeq ORDER BY r.sequenceNum ASC")
    List<AuditRecordEntity> findChainRange(@Param("tenantId") UUID tenantId,
                                            @Param("fromSeq") long fromSeq,
                                            @Param("toSeq") long toSeq);
}
