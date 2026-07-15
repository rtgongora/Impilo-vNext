package zw.gov.mohcc.impilo.simba.social.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.social.entity.SocialNotificationEventEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SocialNotificationEventRepository
        extends JpaRepository<SocialNotificationEventEntity, Long> {

    Optional<SocialNotificationEventEntity> findByNotificationIdAndTenantId(
            UUID notificationId, UUID tenantId);

    /** Recipient inbox newest-first with keyset by id; cursor null → first page. */
    @Query("""
            SELECT n FROM SocialNotificationEventEntity n
            WHERE n.tenantId = :tenantId AND n.recipientCpid = :cpid
              AND (:cursor IS NULL OR n.id < :cursor)
            ORDER BY n.id DESC
            """)
    List<SocialNotificationEventEntity> inbox(@Param("tenantId") UUID tenantId,
                                              @Param("cpid") String cpid,
                                              @Param("cursor") Long cursor,
                                              Pageable pageable);

    long countByTenantIdAndRecipientCpidAndStatus(UUID tenantId, String recipientCpid, String status);
}
