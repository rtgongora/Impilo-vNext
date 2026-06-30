package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureNoteEntity;

import java.util.Optional;
import java.util.UUID;

public interface ProcedureNoteRepository extends JpaRepository<ProcedureNoteEntity, UUID> {
    Optional<ProcedureNoteEntity> findByEpisodeId(UUID episodeId);
}
