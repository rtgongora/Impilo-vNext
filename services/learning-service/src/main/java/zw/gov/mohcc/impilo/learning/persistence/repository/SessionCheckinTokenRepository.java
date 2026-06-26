package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.SessionCheckinTokenEntity;

public interface SessionCheckinTokenRepository extends JpaRepository<SessionCheckinTokenEntity, UUID> {

    Optional<SessionCheckinTokenEntity> findByTenantIdAndToken(UUID tenantId, String token);
}
