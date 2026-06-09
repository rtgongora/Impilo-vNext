package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.simba.persistence.entity.CoachingNudgeEntity;

import java.util.List;
import java.util.UUID;

public interface CoachingNudgeRepository extends JpaRepository<CoachingNudgeEntity, Long> {

    List<CoachingNudgeEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);
}
