package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ClerkingVisitAttestationEntity;

import java.util.List;
import java.util.UUID;

public interface ClerkingVisitAttestationRepository extends JpaRepository<ClerkingVisitAttestationEntity, UUID> {
    List<ClerkingVisitAttestationEntity> findByTenantIdAndSubjectCpidAndEncounterIdOrderByCreatedAtDesc(
            UUID tenantId, String subjectCpid, String encounterId);
}
