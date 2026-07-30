package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureReturnToTheatreEntity;

import java.util.List;
import java.util.UUID;

public interface ProcedureReturnToTheatreRepository extends JpaRepository<ProcedureReturnToTheatreEntity, UUID> {

    List<ProcedureReturnToTheatreEntity> findByEpisodeIdOrderBySeqAsc(UUID episodeId);

    long countByEpisodeId(UUID episodeId);
}
