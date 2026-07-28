package zw.gov.mohcc.impilo.support.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.support.domain.SupportElevatedAccessGrantEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SupportElevatedAccessGrantRepository extends JpaRepository<SupportElevatedAccessGrantEntity, UUID> {
    List<SupportElevatedAccessGrantEntity> findBySupportAssignmentIdAndStatus(UUID supportAssignmentId, String status);
    List<SupportElevatedAccessGrantEntity> findByTicketId(UUID ticketId);

    /** Used by the expiry sweep: ACTIVE grants past their expiry that haven't been auto-expired yet. */
    List<SupportElevatedAccessGrantEntity> findByStatusAndExpiresAtBefore(String status, OffsetDateTime cutoff);
}
