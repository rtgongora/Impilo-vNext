package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureImplantEntity;

import java.util.List;
import java.util.UUID;

public interface ProcedureImplantRepository extends JpaRepository<ProcedureImplantEntity, UUID> {

    List<ProcedureImplantEntity> findByEpisodeIdOrderBySeqAsc(UUID episodeId);

    int countByEpisodeId(UUID episodeId);

    List<ProcedureImplantEntity> findByTenantIdAndUdi(UUID tenantId, String udi);

    List<ProcedureImplantEntity> findByTenantIdAndLotNumber(UUID tenantId, String lotNumber);
}
