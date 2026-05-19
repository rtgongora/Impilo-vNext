package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.nhume.domain.DeliveryWebhookEventEntity;

public interface DeliveryWebhookEventRepository extends JpaRepository<DeliveryWebhookEventEntity, Long> {
}
