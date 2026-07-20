package zw.gov.mohcc.impilo.ndr.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.ndr.domain.ReleasedEncounterEntity;

import java.util.UUID;

public interface ReleasedEncounterRepository extends JpaRepository<ReleasedEncounterEntity, UUID> {

    Page<ReleasedEncounterEntity> findByTenantIdAndDatasetRef(
            UUID tenantId, String datasetRef, Pageable pageable);

    long countByTenantIdAndDatasetRef(UUID tenantId, String datasetRef);

    long deleteByTenantIdAndDatasetRef(UUID tenantId, String datasetRef);
}
