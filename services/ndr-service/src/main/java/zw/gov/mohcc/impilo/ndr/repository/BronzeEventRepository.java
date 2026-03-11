package zw.gov.mohcc.impilo.ndr.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.ndr.domain.BronzeEventEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BronzeEventRepository extends JpaRepository<BronzeEventEntity, UUID> {

    Optional<BronzeEventEntity> findByTenantIdAndPodIdAndEventId(UUID tenantId, String podId, String eventId);

    Optional<BronzeEventEntity> findByTenantIdAndPodIdAndIdempotencyKey(UUID tenantId, String podId, String idempotencyKey);

    Page<BronzeEventEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<BronzeEventEntity> findByTenantIdAndEventType(UUID tenantId, String eventType, Pageable pageable);

    Page<BronzeEventEntity> findByTenantIdAndSubjectId(UUID tenantId, String subjectId, Pageable pageable);

    Page<BronzeEventEntity> findByTenantIdAndEventTypeAndSubjectId(UUID tenantId, String eventType, String subjectId, Pageable pageable);

    @Query("SELECT b FROM BronzeEventEntity b WHERE b.tenantId = :tenantId " +
            "AND b.eventType LIKE :eventTypePattern")
    List<BronzeEventEntity> findByTenantIdAndEventTypeLike(
            @Param("tenantId") UUID tenantId,
            @Param("eventTypePattern") String eventTypePattern);
}
