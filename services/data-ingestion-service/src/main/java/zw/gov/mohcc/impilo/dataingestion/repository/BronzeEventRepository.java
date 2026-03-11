package zw.gov.mohcc.impilo.dataingestion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.dataingestion.domain.BronzeEventEntity;

import java.util.Optional;
import java.util.UUID;

public interface BronzeEventRepository extends JpaRepository<BronzeEventEntity, UUID> {

    Optional<BronzeEventEntity> findByTenantIdAndPodIdAndEventId(UUID tenantId, String podId, String eventId);

    Optional<BronzeEventEntity> findByTenantIdAndPodIdAndIdempotencyKey(UUID tenantId, String podId, String idempotencyKey);

    long countByTenantIdAndPodIdAndEventId(UUID tenantId, String podId, String eventId);
}
