package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.ReconQueueEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.ReconStatus;

import java.util.UUID;

@Repository
public interface ReconQueueRepository extends JpaRepository<ReconQueueEntity, String> {

    Page<ReconQueueEntity> findByTenantIdAndStatus(UUID tenantId, ReconStatus status, Pageable pageable);
}
