package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.experience.domain.AuditLogEntry;

import java.util.Optional;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID> {

    @Query("""
        SELECT a FROM AuditLogEntry a
        WHERE a.tenantId = :tenantId
        ORDER BY a.occurredAt DESC
        """)
    Page<AuditLogEntry> findByTenantId(
            @Param("tenantId") String tenantId,
            Pageable pageable);

    Optional<AuditLogEntry> findByIdAndTenantId(UUID id, String tenantId);
}
