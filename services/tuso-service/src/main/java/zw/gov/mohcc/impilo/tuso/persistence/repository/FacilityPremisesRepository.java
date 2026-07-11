package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityPremisesEntity;

import java.util.List;
import java.util.UUID;

public interface FacilityPremisesRepository extends JpaRepository<FacilityPremisesEntity, UUID> {
    List<FacilityPremisesEntity> findByTenantIdAndStatus(UUID tenantId, String status);
}
