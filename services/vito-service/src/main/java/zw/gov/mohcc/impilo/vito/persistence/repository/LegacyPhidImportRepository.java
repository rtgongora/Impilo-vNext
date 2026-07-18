package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.LegacyPhidImportEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface LegacyPhidImportRepository extends JpaRepository<LegacyPhidImportEntity, Long> {

    List<LegacyPhidImportEntity> findByTenantIdAndBatchId(UUID tenantId, UUID batchId);

    long countByTenantIdAndPhid(UUID tenantId, String phid);
}
