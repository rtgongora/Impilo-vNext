package zw.gov.mohcc.impilo.devportal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.devportal.domain.OutboxEventEntity;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {
}
