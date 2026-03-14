package zw.gov.mohcc.impilo.schemaregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.schemaregistry.domain.OutboxEventEntity;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {
}
