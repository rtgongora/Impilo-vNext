package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureChecklistItemEntity;

import java.util.List;
import java.util.UUID;

public interface ProcedureChecklistItemRepository extends JpaRepository<ProcedureChecklistItemEntity, UUID> {
    List<ProcedureChecklistItemEntity> findByEpisodeIdOrderByPhaseAscItemCodeAsc(UUID episodeId);
    List<ProcedureChecklistItemEntity> findByEpisodeIdAndPhaseOrderByItemCodeAsc(UUID episodeId, String phase);
}
