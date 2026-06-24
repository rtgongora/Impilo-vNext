package zw.gov.mohcc.impilo.datagovernance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.datagovernance.domain.PrivacyDisplayPreferenceEntity;

import java.util.Optional;
import java.util.UUID;

public interface PrivacyDisplayPreferenceRepository
        extends JpaRepository<PrivacyDisplayPreferenceEntity, UUID> {

    Optional<PrivacyDisplayPreferenceEntity> findByTenantIdAndUserId(UUID tenantId, String userId);
}
