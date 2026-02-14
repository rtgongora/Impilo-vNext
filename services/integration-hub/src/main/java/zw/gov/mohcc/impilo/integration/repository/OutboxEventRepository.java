package zw.gov.mohcc.impilo.integration.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.integration.domain.OutboxEventEntity;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, String> {
}
