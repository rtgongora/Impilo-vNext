package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureSafetyEventEntity;

import java.util.List;
import java.util.UUID;

public interface ProcedureSafetyEventRepository extends JpaRepository<ProcedureSafetyEventEntity, UUID> {
    List<ProcedureSafetyEventEntity> findByEpisodeIdOrderByReportedAtDesc(UUID episodeId);
}
