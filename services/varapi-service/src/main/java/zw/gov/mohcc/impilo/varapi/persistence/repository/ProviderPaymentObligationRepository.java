package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderPaymentObligationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderPaymentObligationRepository extends JpaRepository<ProviderPaymentObligationEntity, Long> {

    List<ProviderPaymentObligationEntity> findByTenantIdAndProvider_Id(UUID tenantId, Long providerId);

    Optional<ProviderPaymentObligationEntity> findByIdAndTenantId(Long id, UUID tenantId);

    Optional<ProviderPaymentObligationEntity> findByTenantIdAndMushexIntentId(UUID tenantId, String mushexIntentId);

    /** Intent id is unique across MusheX; used when Kafka events do not embed tenant id. */
    Optional<ProviderPaymentObligationEntity> findTopByMushexIntentIdOrderByIdAsc(String mushexIntentId);
}
