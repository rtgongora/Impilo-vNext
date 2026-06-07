package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.CrossmatchRequestEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CrossmatchRequestRepository extends JpaRepository<CrossmatchRequestEntity, Long> {
    Optional<CrossmatchRequestEntity> findByRequestIdAndTenantId(UUID requestId, UUID tenantId);
    List<CrossmatchRequestEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
