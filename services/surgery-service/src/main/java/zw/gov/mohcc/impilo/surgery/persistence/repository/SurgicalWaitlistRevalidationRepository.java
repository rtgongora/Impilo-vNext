package zw.gov.mohcc.impilo.surgery.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.surgery.persistence.entity.SurgicalWaitlistRevalidationEntity;

import java.util.List;
import java.util.UUID;

public interface SurgicalWaitlistRevalidationRepository extends JpaRepository<SurgicalWaitlistRevalidationEntity, UUID> {

    List<SurgicalWaitlistRevalidationEntity> findBySurgicalEpisodeIdAndTenantIdOrderByRevalidatedAtDesc(
            UUID surgicalEpisodeId, UUID tenantId);
}
