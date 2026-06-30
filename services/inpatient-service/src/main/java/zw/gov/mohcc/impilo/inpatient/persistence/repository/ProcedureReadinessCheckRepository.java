package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureReadinessCheckEntity;

import java.util.List;
import java.util.UUID;

public interface ProcedureReadinessCheckRepository extends JpaRepository<ProcedureReadinessCheckEntity, UUID> {
    List<ProcedureReadinessCheckEntity> findByEpisodeIdOrderByCheckedAtDesc(UUID episodeId);
}
