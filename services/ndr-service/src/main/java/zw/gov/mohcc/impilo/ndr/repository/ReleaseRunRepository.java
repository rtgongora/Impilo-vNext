package zw.gov.mohcc.impilo.ndr.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.ndr.domain.ReleaseRunEntity;

import java.util.UUID;

public interface ReleaseRunRepository extends JpaRepository<ReleaseRunEntity, UUID> {

    Page<ReleaseRunEntity> findByTenantIdAndDatasetRefOrderByBuiltAtDesc(
            UUID tenantId, String datasetRef, Pageable pageable);
}
