package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureDischargeEntity;

import java.util.Optional;
import java.util.UUID;

public interface ProcedureDischargeRepository extends JpaRepository<ProcedureDischargeEntity, UUID> {

    Optional<ProcedureDischargeEntity> findByEpisodeId(UUID episodeId);
}
