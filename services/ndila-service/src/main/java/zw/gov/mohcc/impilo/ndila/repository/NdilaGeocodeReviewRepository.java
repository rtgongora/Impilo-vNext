package zw.gov.mohcc.impilo.ndila.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ndila.domain.NdilaGeocodeReviewEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NdilaGeocodeReviewRepository extends JpaRepository<NdilaGeocodeReviewEntity, UUID> {

    Optional<NdilaGeocodeReviewEntity> findByTenantIdAndOwnerServiceAndOwnerEntityId(
            UUID tenantId, String ownerService, String ownerEntityId);

    List<NdilaGeocodeReviewEntity> findByTenantIdAndStatus(UUID tenantId, String status);
}
