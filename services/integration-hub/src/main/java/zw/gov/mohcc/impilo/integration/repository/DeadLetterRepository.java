package zw.gov.mohcc.impilo.integration.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.integration.domain.DeadLetterEntity;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterEntity, String> {

    Page<DeadLetterEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    Page<DeadLetterEntity> findByTenantIdAndResolvedOrderByCreatedAtDesc(String tenantId, boolean resolved, Pageable pageable);
}
