package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ConsultationEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsultationRepository extends JpaRepository<ConsultationEntity, UUID> {

    List<ConsultationEntity> findByTenantIdAndSubjectCpidOrderByRequestedAtDesc(UUID tenantId, String subjectCpid);

    Optional<ConsultationEntity> findByTenantIdAndConsultationId(UUID tenantId, UUID consultationId);

    /** The receiving team's worklist: what has been asked of them and not yet answered. */
    List<ConsultationEntity> findByTenantIdAndAskedOfServiceAndStatusInOrderByRequestedAtAsc(
            UUID tenantId, String askedOfService, Collection<String> statuses);
}
