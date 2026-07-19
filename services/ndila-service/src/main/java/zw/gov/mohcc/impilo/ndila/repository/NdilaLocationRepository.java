package zw.gov.mohcc.impilo.ndila.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NdilaLocationRepository extends JpaRepository<NdilaLocationEntity, UUID> {

    List<NdilaLocationEntity> findByTenantId(UUID tenantId);

    List<NdilaLocationEntity> findByTenantIdAndOwnerServiceAndOwnerEntityId(
            UUID tenantId, String ownerService, String ownerEntityId);

    List<NdilaLocationEntity> findByTenantIdAndDistrict(UUID tenantId, String district);

    List<NdilaLocationEntity> findByTenantIdAndLocationType(UUID tenantId, String locationType);

    List<NdilaLocationEntity> findByTenantIdAndAnchorId(UUID tenantId, UUID anchorId);

    Optional<NdilaLocationEntity> findByTenantIdAndOwnerServiceAndOwnerEntityTypeAndOwnerEntityId(
            UUID tenantId, String ownerService, String ownerEntityType, String ownerEntityId);
}
