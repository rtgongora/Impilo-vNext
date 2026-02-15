package zw.gov.mohcc.impilo.ndr.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ndr.persistence.entity.MaterializedViewEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface MaterializedViewRepository extends JpaRepository<MaterializedViewEntity, String> {

    List<MaterializedViewEntity> findByTenantIdAndDatasetKey(UUID tenantId, String datasetKey);
}
