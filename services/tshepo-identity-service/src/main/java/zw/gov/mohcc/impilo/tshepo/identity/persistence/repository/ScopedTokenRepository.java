package zw.gov.mohcc.impilo.tshepo.identity.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.ScopedTokenEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScopedTokenRepository extends JpaRepository<ScopedTokenEntity, UUID> {

    Optional<ScopedTokenEntity> findByJti(String jti);

    Optional<ScopedTokenEntity> findByTenantIdAndJti(UUID tenantId, String jti);
}
