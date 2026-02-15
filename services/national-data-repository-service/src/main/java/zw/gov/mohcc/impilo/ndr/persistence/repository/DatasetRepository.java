package zw.gov.mohcc.impilo.ndr.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ndr.persistence.entity.DatasetEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DatasetRepository extends JpaRepository<DatasetEntity, String> {

    List<DatasetEntity> findByTenantId(UUID tenantId);

    Optional<DatasetEntity> findByTenantIdAndDatasetKey(UUID tenantId, String datasetKey);

    Optional<DatasetEntity> findByTenantIdAndDatasetId(UUID tenantId, String datasetId);
}
