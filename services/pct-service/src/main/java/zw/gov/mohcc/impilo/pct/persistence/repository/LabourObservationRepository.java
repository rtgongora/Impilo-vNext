package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.LabourObservationEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface LabourObservationRepository extends JpaRepository<LabourObservationEntity, UUID> {

    List<LabourObservationEntity> findByTenantIdAndSubjectCpidOrderByObservedAtDesc(UUID tenantId, String subjectCpid);

    List<LabourObservationEntity> findByPartographSessionIdOrderByObservedAtAsc(UUID partographSessionId);

    List<LabourObservationEntity> findByTenantIdAndSubjectCpidAndEncounterIdOrderByObservedAtDesc(
            UUID tenantId, String subjectCpid, String encounterId);
}
