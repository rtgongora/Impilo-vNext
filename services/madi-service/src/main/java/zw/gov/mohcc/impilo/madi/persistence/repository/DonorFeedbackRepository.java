package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.DonorFeedbackEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DonorFeedbackRepository extends JpaRepository<DonorFeedbackEntity, Long> {
    Optional<DonorFeedbackEntity> findByFeedbackIdAndTenantId(UUID feedbackId, UUID tenantId);
    List<DonorFeedbackEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
