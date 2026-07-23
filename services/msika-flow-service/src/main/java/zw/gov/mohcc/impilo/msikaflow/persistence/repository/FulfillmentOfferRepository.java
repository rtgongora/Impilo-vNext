package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.domain.OfferStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FulfillmentOfferRepository extends JpaRepository<FulfillmentOfferEntity, String> {

    List<FulfillmentOfferEntity> findByRequestId(String requestId);

    List<FulfillmentOfferEntity> findByRequestIdAndStatus(String requestId, OfferStatus status);

    Optional<FulfillmentOfferEntity> findByOfferIdAndTenantId(String offerId, UUID tenantId);

    List<FulfillmentOfferEntity> findByStatusAndTtlExpiresAtBefore(OfferStatus status, OffsetDateTime cutoff);
}
