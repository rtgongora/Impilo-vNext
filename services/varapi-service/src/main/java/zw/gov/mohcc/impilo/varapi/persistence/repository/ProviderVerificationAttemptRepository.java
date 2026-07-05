package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderVerificationAttemptEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderVerificationAttemptRepository
        extends JpaRepository<ProviderVerificationAttemptEntity, Long> {

    List<ProviderVerificationAttemptEntity> findByTenantIdAndProvider_IdOrderByAttemptedAtDescIdDesc(
            UUID tenantId, Long providerId);
}
