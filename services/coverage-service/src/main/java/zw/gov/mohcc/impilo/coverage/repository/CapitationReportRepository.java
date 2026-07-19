package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.CapitationReportEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CapitationReportRepository extends JpaRepository<CapitationReportEntity, UUID> {
    List<CapitationReportEntity> findByTenantIdAndProviderIdOrderByCreatedAtDesc(UUID tenantId, String providerId);
}
