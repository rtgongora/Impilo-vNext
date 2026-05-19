package zw.gov.mohcc.impilo.ndila.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ndila.domain.NdilaOutboxEventEntity;

@Repository
public interface NdilaOutboxRepository extends JpaRepository<NdilaOutboxEventEntity, Long> {
}
