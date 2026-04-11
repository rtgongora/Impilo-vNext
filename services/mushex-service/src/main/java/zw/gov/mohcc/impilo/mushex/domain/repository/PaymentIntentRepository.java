package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.IntentStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntentEntity, String> {

    Optional<PaymentIntentEntity> findByIdempotencyKey(String idempotencyKey);

    Page<PaymentIntentEntity> findByTenantIdAndStatus(UUID tenantId, IntentStatus status, Pageable pageable);

    List<PaymentIntentEntity> findByTenantIdAndStatusAndCreatedAtBetween(
            UUID tenantId,
            IntentStatus status,
            OffsetDateTime start,
            OffsetDateTime end
    );

    List<PaymentIntentEntity> findBySourceTypeAndSourceId(SourceType sourceType, String sourceId);

    long countBySourceTypeAndSourceIdAndStatus(SourceType sourceType, String sourceId, IntentStatus status);

    long countByTenantIdAndFacilityIdAndCreatedAtAfter(UUID tenantId, UUID facilityId, OffsetDateTime after);
}
