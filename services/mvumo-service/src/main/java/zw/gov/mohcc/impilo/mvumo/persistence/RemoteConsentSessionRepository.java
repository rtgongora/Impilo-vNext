package zw.gov.mohcc.impilo.mvumo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RemoteConsentSessionRepository extends JpaRepository<RemoteConsentSessionEntity, UUID> {

    Optional<RemoteConsentSessionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<RemoteConsentSessionEntity> findByTokenHash(String tokenHash);
}
