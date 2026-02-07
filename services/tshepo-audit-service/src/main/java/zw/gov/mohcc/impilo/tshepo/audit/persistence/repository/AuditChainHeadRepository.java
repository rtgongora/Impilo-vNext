package zw.gov.mohcc.impilo.tshepo.audit.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.AuditChainHeadEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuditChainHeadRepository extends JpaRepository<AuditChainHeadEntity, Long> {

    /**
     * Acquire a PESSIMISTIC_WRITE lock on the chain head for the given tenant.
     * This ensures gapless sequencing: only one transaction can increment
     * the sequence number at a time for a given tenant.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM AuditChainHeadEntity h WHERE h.tenantId = :tenantId")
    Optional<AuditChainHeadEntity> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);

    /**
     * Non-locking read of chain head (for verification and read-only queries).
     */
    Optional<AuditChainHeadEntity> findByTenantId(UUID tenantId);
}
