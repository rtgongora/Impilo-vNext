package zw.gov.mohcc.impilo.tshepo.consent.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConsentAuditRepository extends JpaRepository<ConsentAuditEntity, Long> {

    /**
     * Find all audit entries for a specific consent directive.
     */
    List<ConsentAuditEntity> findByConsentIdOrderByCreatedAtDesc(UUID consentId);

    /**
     * Paginated audit trail for a tenant.
     */
    Page<ConsentAuditEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
}
