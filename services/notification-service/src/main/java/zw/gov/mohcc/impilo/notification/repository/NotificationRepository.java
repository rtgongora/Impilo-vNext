package zw.gov.mohcc.impilo.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.notification.domain.NotificationEntity;
import zw.gov.mohcc.impilo.notification.domain.NotificationStatus;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {

    List<NotificationEntity> findByStatusOrderByCreatedAtAsc(NotificationStatus status);

    List<NotificationEntity> findByTenantIdAndPatientRefAndStatus(
            String tenantId, String patientRef, NotificationStatus status);

    Page<NotificationEntity> findByTenantId(String tenantId, Pageable pageable);
}
