package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.HealthSummaryCredentialEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface HealthSummaryCredentialRepository extends JpaRepository<HealthSummaryCredentialEntity, Long> {

    List<HealthSummaryCredentialEntity> findByTenantIdAndHealthIdAndStatus(
            UUID tenantId, UUID healthId, String status);

    List<HealthSummaryCredentialEntity> findByTenantIdAndCardIdAndStatus(
            UUID tenantId, Long cardId, String status);
}
