package zw.gov.mohcc.impilo.datagovernance.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.datagovernance.domain.DatasetEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DatasetRepository extends JpaRepository<DatasetEntity, UUID> {

    Optional<DatasetEntity> findByTenantIdAndName(UUID tenantId, String name);

    List<DatasetEntity> findByTenantId(UUID tenantId);

    Page<DatasetEntity> findByTenantId(UUID tenantId, Pageable pageable);
}
