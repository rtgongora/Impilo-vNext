package zw.gov.mohcc.impilo.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.notification.domain.NotificationEntity;
import zw.gov.mohcc.impilo.notification.domain.NotificationStatus;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {

    List<NotificationEntity> findByStatusOrderByCreatedAtAsc(NotificationStatus status);

    /**
     * Due notifications for dispatch (TM-B9/B1): given status, only those whose
     * not_before gate is unset (immediate) or has already elapsed. Ordered oldest-first;
     * the {@link Pageable} caps the batch size at the DB level.
     */
    @Query("SELECT n FROM NotificationEntity n WHERE n.status = :status "
            + "AND (n.notBefore IS NULL OR n.notBefore <= :now) ORDER BY n.createdAt ASC")
    List<NotificationEntity> findDue(
            @Param("status") NotificationStatus status,
            @Param("now") OffsetDateTime now,
            Pageable pageable);

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
