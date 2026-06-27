package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.persistence.entity.AdminConfigEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdminConfigRepository extends JpaRepository<AdminConfigEntity, UUID> {

    Optional<AdminConfigEntity> findByTenantIdAndFacilityIdAndConfigType(
            UUID tenantId, UUID facilityId, String configType);

    Optional<AdminConfigEntity> findByTenantIdAndFacilityIdIsNullAndConfigType(
            UUID tenantId, String configType);
}
