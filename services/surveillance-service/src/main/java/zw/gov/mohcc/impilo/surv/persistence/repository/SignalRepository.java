package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.surv.core.SignalStatus;
import zw.gov.mohcc.impilo.surv.persistence.entity.SignalEntity;

@Repository
public interface SignalRepository extends JpaRepository<SignalEntity, Long> {

    List<SignalEntity> findByTenantId(UUID tenantId);

    List<SignalEntity> findByTenantIdAndEventTypeAndStatus(UUID tenantId, String eventType, SignalStatus status);
}
