package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.FundoLearningLinkEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FundoLearningLinkRepository extends JpaRepository<FundoLearningLinkEntity, Long> {

    Optional<FundoLearningLinkEntity> findByTenantIdAndProvider_Id(UUID tenantId, Long providerId);
}
