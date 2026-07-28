package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ResuscitationMedicationEntity;

import java.util.List;
import java.util.UUID;

public interface ResuscitationMedicationRepository extends JpaRepository<ResuscitationMedicationEntity, UUID> {
    List<ResuscitationMedicationEntity> findByActivationIdOrderByAdministeredAtAsc(UUID activationId);
    List<ResuscitationMedicationEntity> findByTraumaEpisodeIdOrderByAdministeredAtAsc(UUID traumaEpisodeId);
}
