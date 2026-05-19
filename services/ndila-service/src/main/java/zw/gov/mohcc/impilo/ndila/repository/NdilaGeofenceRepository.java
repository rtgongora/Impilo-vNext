package zw.gov.mohcc.impilo.ndila.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ndila.domain.NdilaGeofenceEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface NdilaGeofenceRepository extends JpaRepository<NdilaGeofenceEntity, UUID> {

    List<NdilaGeofenceEntity> findByTenantIdAndActiveTrue(UUID tenantId);

    List<NdilaGeofenceEntity> findByTenantIdAndOwnerServiceAndOwnerEntityIdAndActiveTrue(
            UUID tenantId, String ownerService, String ownerEntityId);

    List<NdilaGeofenceEntity> findByTenantIdAndGeofenceTypeAndActiveTrue(
            UUID tenantId, String geofenceType);
}
