package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCommitteeReviewEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderCommitteeReviewRepository extends JpaRepository<ProviderCommitteeReviewEntity, Long> {

    List<ProviderCommitteeReviewEntity> findByProviderId(Long providerId);

    List<ProviderCommitteeReviewEntity> findByCaseId(Long caseId);

    List<ProviderCommitteeReviewEntity> findByTenantIdAndCommitteeType(UUID tenantId, String committeeType);
}