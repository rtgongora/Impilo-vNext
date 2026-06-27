package zw.gov.mohcc.impilo.tshepo.authz.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.TotpEnrolmentEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TotpEnrolmentRepository
        extends JpaRepository<TotpEnrolmentEntity, TotpEnrolmentEntity.Key> {

    Optional<TotpEnrolmentEntity> findByTenantIdAndActorId(UUID tenantId, String actorId);

    Optional<TotpEnrolmentEntity> findByTenantIdAndActorIdAndStatus(
            UUID tenantId, String actorId, String status);
}
