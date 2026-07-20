package zw.gov.mohcc.impilo.tshepo.keys.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.ManagedSecretEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ManagedSecretRepository extends JpaRepository<ManagedSecretEntity, Long> {

    Optional<ManagedSecretEntity> findByTenantIdAndSecretRefAndStatus(
            UUID tenantId, String secretRef, String status);
}
