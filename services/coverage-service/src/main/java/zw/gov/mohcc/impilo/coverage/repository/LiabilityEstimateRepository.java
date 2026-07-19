package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.LiabilityEstimateEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface LiabilityEstimateRepository extends JpaRepository<LiabilityEstimateEntity, UUID> {
    List<LiabilityEstimateEntity> findByTenantIdAndMemberCpidOrderByCreatedAtDesc(UUID tenantId, String memberCpid);
}
