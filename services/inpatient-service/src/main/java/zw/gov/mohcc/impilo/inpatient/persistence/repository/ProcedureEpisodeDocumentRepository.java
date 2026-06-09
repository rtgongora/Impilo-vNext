package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureEpisodeDocumentEntity;

import java.util.List;
import java.util.UUID;

public interface ProcedureEpisodeDocumentRepository extends JpaRepository<ProcedureEpisodeDocumentEntity, UUID> {
    List<ProcedureEpisodeDocumentEntity> findByEpisodeIdOrderByRecordedAtDesc(UUID episodeId);
}
