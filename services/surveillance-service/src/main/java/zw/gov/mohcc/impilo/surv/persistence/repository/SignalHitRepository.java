package zw.gov.mohcc.impilo.surv.persistence.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.surv.persistence.entity.SignalHitEntity;

@Repository
public interface SignalHitRepository extends JpaRepository<SignalHitEntity, Long> {

    Page<SignalHitEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<SignalHitEntity> findBySignalId(Long signalId, Pageable pageable);
}
