package zw.gov.mohcc.impilo.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.notification.domain.NotificationRequestEntity;

@Repository
public interface NotificationRequestRepository extends JpaRepository<NotificationRequestEntity, String> {
}
