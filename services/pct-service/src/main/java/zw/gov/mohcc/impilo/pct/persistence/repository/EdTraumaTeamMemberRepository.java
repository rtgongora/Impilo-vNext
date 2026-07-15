package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.EdTraumaTeamMemberEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EdTraumaTeamMemberRepository extends JpaRepository<EdTraumaTeamMemberEntity, UUID> {
    List<EdTraumaTeamMemberEntity> findByTraumaIdOrderByCreatedAtAsc(UUID traumaId);
    Optional<EdTraumaTeamMemberEntity> findByIdAndTenantId(UUID id, UUID tenantId);
    Optional<EdTraumaTeamMemberEntity> findByTraumaIdAndWorkforceProfileId(UUID traumaId, String workforceProfileId);
    /** Members still un-acked past the no-ack cutoff — the escalation set. */
    List<EdTraumaTeamMemberEntity> findByTraumaIdAndAckStatusAndNotifiedAtBefore(
            UUID traumaId, String ackStatus, OffsetDateTime cutoff);
}
