package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.InstrumentSetCssdCycleEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstrumentSetCssdCycleRepository
        extends JpaRepository<InstrumentSetCssdCycleEntity, UUID> {

    List<InstrumentSetCssdCycleEntity> findByTenantIdAndInstrumentSetIdOrderByCycleNoDesc(
            UUID tenantId, UUID instrumentSetId);

    Optional<InstrumentSetCssdCycleEntity> findFirstByTenantIdAndInstrumentSetIdOrderByCycleNoDesc(
            UUID tenantId, UUID instrumentSetId);

    long countByTenantIdAndInstrumentSetId(UUID tenantId, UUID instrumentSetId);

    List<InstrumentSetCssdCycleEntity> findByEpisodeRefOrderByCycleNoAsc(String episodeRef);
}
