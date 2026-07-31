package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.rito.persistence.entity.MpdsrReviewEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MpdsrReviewRepository extends JpaRepository<MpdsrReviewEntity, UUID> {

    Optional<MpdsrReviewEntity> findByTenantIdAndDeathCaseId(UUID tenantId, UUID deathCaseId);

    Optional<MpdsrReviewEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<MpdsrReviewEntity> findByTenantIdOrderByOpenedAtDesc(UUID tenantId);

    List<MpdsrReviewEntity> findByTenantIdAndStatusOrderByOpenedAtDesc(UUID tenantId, String status);
}
