package zw.gov.mohcc.impilo.dispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.dispatch.domain.DispatchEventEntity;

import java.util.UUID;

public interface DispatchEventRepository extends JpaRepository<DispatchEventEntity, Long> {

    long countByJobId(UUID jobId);
}
