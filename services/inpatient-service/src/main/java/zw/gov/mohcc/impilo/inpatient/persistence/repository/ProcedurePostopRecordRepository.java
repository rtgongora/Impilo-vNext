package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedurePostopRecordEntity;

import java.util.Optional;
import java.util.UUID;

public interface ProcedurePostopRecordRepository extends JpaRepository<ProcedurePostopRecordEntity, UUID> {
    Optional<ProcedurePostopRecordEntity> findByEpisodeId(UUID episodeId);
}
