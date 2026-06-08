package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodIssueEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodIssueRepository extends JpaRepository<BloodIssueEntity, Long> {
    Optional<BloodIssueEntity> findByIssueIdAndTenantId(UUID issueId, UUID tenantId);
    List<BloodIssueEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
