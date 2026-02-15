package zw.gov.mohcc.impilo.ndr.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ndr.persistence.entity.DatasetAccessPolicyEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface DatasetAccessPolicyRepository extends JpaRepository<DatasetAccessPolicyEntity, String> {

    List<DatasetAccessPolicyEntity> findByTenantIdAndDatasetId(UUID tenantId, String datasetId);
}
