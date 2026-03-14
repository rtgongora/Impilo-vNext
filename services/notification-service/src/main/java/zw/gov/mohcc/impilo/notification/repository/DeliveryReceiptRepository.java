package zw.gov.mohcc.impilo.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.notification.domain.DeliveryReceiptEntity;

import java.util.List;

@Repository
public interface DeliveryReceiptRepository extends JpaRepository<DeliveryReceiptEntity, String> {

    List<DeliveryReceiptEntity> findByNotificationId(String notificationId);

    Page<DeliveryReceiptEntity> findByTenantIdAndStatus(String tenantId, String status, Pageable pageable);
}
