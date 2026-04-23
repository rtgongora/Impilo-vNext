package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilReviewEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderCouncilReviewRepository extends JpaRepository<ProviderCouncilReviewEntity, Long> {

    List<ProviderCouncilReviewEntity> findByTenantIdAndApplication_IdOrderByReviewedAtDesc(UUID tenantId, Long applicationId);
}
