package zw.gov.mohcc.impilo.mvumo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ConsentEventRepository extends JpaRepository<ConsentEventEntity, Long> {

    List<ConsentEventEntity> findByTenantIdAndConsentIdOrderByCreatedAtDesc(UUID tenantId, UUID consentId);
}
