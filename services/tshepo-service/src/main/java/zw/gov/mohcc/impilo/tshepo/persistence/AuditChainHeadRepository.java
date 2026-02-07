package zw.gov.mohcc.impilo.tshepo.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditChainHeadRepository extends JpaRepository<AuditChainHeadEntity, UUID> {

    /**
     * Lock the chain head row for the given tenant using SELECT ... FOR UPDATE.
     * This serializes all concurrent audit chain writes for the same tenant.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM AuditChainHeadEntity h WHERE h.tenantId = :tenantId")
    AuditChainHeadEntity findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}
