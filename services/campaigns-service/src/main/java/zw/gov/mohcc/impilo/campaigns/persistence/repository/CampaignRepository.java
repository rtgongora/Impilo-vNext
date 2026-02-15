package zw.gov.mohcc.impilo.campaigns.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.CampaignEntity;

@Repository
public interface CampaignRepository extends JpaRepository<CampaignEntity, Long> {

    Page<CampaignEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<CampaignEntity> findByIdAndTenantId(Long id, UUID tenantId);
}
