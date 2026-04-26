package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.TariffUploadBatchEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TariffUploadBatchRepository extends JpaRepository<TariffUploadBatchEntity, UUID> {

    Optional<TariffUploadBatchEntity> findByUploadBatchIdAndTenantId(UUID uploadBatchId, UUID tenantId);
}
