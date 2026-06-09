package zw.gov.mohcc.impilo.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.notification.domain.NotificationEntity;
import zw.gov.mohcc.impilo.notification.domain.NotificationStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {

    List<NotificationEntity> findByStatusOrderByCreatedAtAsc(NotificationStatus status);

    List<NotificationEntity> findByTenantIdAndPatientRefAndStatus(
            String tenantId, String patientRef, NotificationStatus status);

    Page<NotificationEntity> findByTenantId(String tenantId, Pageable pageable);

    Page<NotificationEntity> findByTenantIdAndInboxRecipient(String tenantId, String inboxRecipient, Pageable pageable);

    Page<NotificationEntity> findByTenantIdAndInboxRecipientIn(
            String tenantId, Collection<String> inboxRecipients, Pageable pageable);

    Optional<NotificationEntity> findByIdAndTenantId(String id, String tenantId);

    long countByTenantIdAndReadAtIsNull(String tenantId);

    long countByTenantIdAndInboxRecipientAndReadAtIsNull(String tenantId, String inboxRecipient);

    long countByTenantIdAndInboxRecipientInAndReadAtIsNull(String tenantId, Collection<String> inboxRecipients);
}
