package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureConsentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcedureConsentRepository extends JpaRepository<ProcedureConsentEntity, UUID> {

    List<ProcedureConsentEntity> findByEpisodeId(UUID episodeId);

    Optional<ProcedureConsentEntity> findByEpisodeIdAndConsentType(UUID episodeId, String consentType);
}
