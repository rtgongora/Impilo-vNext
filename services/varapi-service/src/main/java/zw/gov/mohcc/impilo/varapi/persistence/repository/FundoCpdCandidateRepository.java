package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.FundoCpdCandidateEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FundoCpdCandidateRepository extends JpaRepository<FundoCpdCandidateEntity, Long> {

    List<FundoCpdCandidateEntity> findByTenantIdAndProvider_IdOrderByCreatedAtDesc(UUID tenantId, Long providerId);

    Optional<FundoCpdCandidateEntity> findByIdAndTenantId(Long id, UUID tenantId);

    Optional<FundoCpdCandidateEntity> findByTenantIdAndProvider_IdAndExternalRef(
            UUID tenantId, Long providerId, String externalRef);
}
