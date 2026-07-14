package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureControlledDrugEntity;

import java.util.List;
import java.util.UUID;

public interface ProcedureControlledDrugRepository
        extends JpaRepository<ProcedureControlledDrugEntity, UUID> {

    List<ProcedureControlledDrugEntity> findByEpisodeIdOrderBySeqAsc(UUID episodeId);

    int countByEpisodeId(UUID episodeId);
}
