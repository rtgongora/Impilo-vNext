package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceRequestStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.MarketplaceRequestEntity;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MarketplaceRequestRepository extends JpaRepository<MarketplaceRequestEntity, String> {

    Optional<MarketplaceRequestEntity> findByRequestIdAndTenantId(String requestId, UUID tenantId);

    List<MarketplaceRequestEntity> findByTenantIdAndStatusIn(UUID tenantId, Collection<MarketplaceRequestStatus> statuses);

    List<MarketplaceRequestEntity> findByStatusInAndOfferWindowEndsAtBefore(
            Collection<MarketplaceRequestStatus> statuses, OffsetDateTime cutoff);

    List<MarketplaceRequestEntity> findByStatusInAndSelectionWindowEndsAtBefore(
            Collection<MarketplaceRequestStatus> statuses, OffsetDateTime cutoff);
}
