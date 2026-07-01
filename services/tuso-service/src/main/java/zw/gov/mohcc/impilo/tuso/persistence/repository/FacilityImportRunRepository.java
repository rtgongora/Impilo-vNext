package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityImportRunEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface FacilityImportRunRepository extends JpaRepository<FacilityImportRunEntity, Long> {

    List<FacilityImportRunEntity> findByTenantIdOrderByStartedAtDesc(UUID tenantId);
}
