package zw.gov.mohcc.impilo.surgery.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalPrehabItemEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SurgicalPrehabItemRepository extends JpaRepository<SurgicalPrehabItemEntity, UUID> {

    List<SurgicalPrehabItemEntity> findBySurgicalEpisodeIdAndTenantIdOrderByDomainAsc(
            UUID surgicalEpisodeId, UUID tenantId);

    Optional<SurgicalPrehabItemEntity> findBySurgicalEpisodeIdAndTenantIdAndDomain(
            UUID surgicalEpisodeId, UUID tenantId, String domain);
}
