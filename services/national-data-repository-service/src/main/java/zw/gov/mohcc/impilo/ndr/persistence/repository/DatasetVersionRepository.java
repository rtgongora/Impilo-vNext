package zw.gov.mohcc.impilo.ndr.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ndr.persistence.entity.DatasetVersionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DatasetVersionRepository extends JpaRepository<DatasetVersionEntity, String> {

    List<DatasetVersionEntity> findByTenantIdAndDatasetId(UUID tenantId, String datasetId);

    Optional<DatasetVersionEntity> findTopByDatasetIdOrderByVersionNumberDesc(String datasetId);
}
