package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilAffiliationEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderCouncilAffiliationRepository extends JpaRepository<ProviderCouncilAffiliationEntity, Long> {

    List<ProviderCouncilAffiliationEntity> findByProviderId(Long providerId);

    Optional<ProviderCouncilAffiliationEntity> findByProviderIdAndCouncilId(Long providerId, Long councilId);
}
