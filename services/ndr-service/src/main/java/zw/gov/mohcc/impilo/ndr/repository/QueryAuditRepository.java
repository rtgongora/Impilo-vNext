package zw.gov.mohcc.impilo.ndr.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.ndr.domain.QueryAuditEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface QueryAuditRepository extends JpaRepository<QueryAuditEntity, UUID> {

    Page<QueryAuditEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<QueryAuditEntity> findByTenantIdAndPrincipalId(UUID tenantId, String principalId, Pageable pageable);

    List<QueryAuditEntity> findByTenantIdAndDecidedAtBetween(UUID tenantId, OffsetDateTime from, OffsetDateTime to);

    long countByTenantIdAndDecision(UUID tenantId, String decision);
}
