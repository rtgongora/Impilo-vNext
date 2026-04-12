package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.EncounterEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.EncounterStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EncounterRepository extends JpaRepository<EncounterEntity, String> {
    Optional<EncounterEntity> findByPctJourneyId(String pctJourneyId);
    List<EncounterEntity> findByTenantIdAndFacilityIdAndStatus(UUID tenantId, UUID facilityId, EncounterStatus status);
    List<EncounterEntity> findByPatientCpidOrderByOpenedAtDesc(String patientCpid);

    List<EncounterEntity> findByTenantIdAndPatientCpid(UUID tenantId, String patientCpid);
}
