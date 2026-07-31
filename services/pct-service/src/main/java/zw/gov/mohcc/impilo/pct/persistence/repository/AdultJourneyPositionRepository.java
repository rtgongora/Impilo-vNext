package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.AdultJourneyPositionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdultJourneyPositionRepository extends JpaRepository<AdultJourneyPositionEntity, UUID> {

    Optional<AdultJourneyPositionEntity> findFirstByTenantIdAndSubjectCpidOrderByRecordedAtDesc(
            UUID tenantId, String subjectCpid);

    Optional<AdultJourneyPositionEntity> findFirstByTenantIdAndSubjectCpidAndEncounterIdOrderByRecordedAtDesc(
            UUID tenantId, String subjectCpid, String encounterId);

    Optional<AdultJourneyPositionEntity> findFirstByTenantIdAndSubjectCpidAndJourneyIdOrderByRecordedAtDesc(
            UUID tenantId, String subjectCpid, String journeyId);

    List<AdultJourneyPositionEntity> findByTenantIdAndSubjectCpidOrderByRecordedAtDesc(
            UUID tenantId, String subjectCpid);
}
