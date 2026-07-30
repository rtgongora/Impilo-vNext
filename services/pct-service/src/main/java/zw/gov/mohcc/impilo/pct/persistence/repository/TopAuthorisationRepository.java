package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.TopAuthorisationEntity;

import java.util.List;
import java.util.UUID;

public interface TopAuthorisationRepository extends JpaRepository<TopAuthorisationEntity, UUID> {

    /**
     * A subject's authorisations, newest first.
     *
     * <p>Ordered by {@code recorded_at} rather than {@code certified_on}, because certification is
     * what an AUTHORISED row has and a REQUESTED or DECLINED one does not. Ordering by a column that
     * is null for exactly the rows a clinician most needs to see next would put a pending request at
     * whichever end of the list the database happened to choose.
     */
    List<TopAuthorisationEntity> findByTenantIdAndSubjectCpidOrderByRecordedAtDesc(
            UUID tenantId, String subjectCpid);
}
