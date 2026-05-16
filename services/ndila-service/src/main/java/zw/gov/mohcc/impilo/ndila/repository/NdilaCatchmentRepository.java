package zw.gov.mohcc.impilo.ndila.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ndila.domain.NdilaCatchmentAreaEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface NdilaCatchmentRepository extends JpaRepository<NdilaCatchmentAreaEntity, UUID> {

    List<NdilaCatchmentAreaEntity> findByTenantIdAndActiveTrue(UUID tenantId);

    List<NdilaCatchmentAreaEntity> findByTenantIdAndOwnerServiceAndOwnerEntityId(
            UUID tenantId, String ownerService, String ownerEntityId);
}
