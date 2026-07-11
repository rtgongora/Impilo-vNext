package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ExternalCouncilReviewEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalCouncilReviewRepository extends JpaRepository<ExternalCouncilReviewEntity, UUID> {
    List<ExternalCouncilReviewEntity> findByApplicationIdOrderByRecordedAtDesc(UUID applicationId);
    Optional<ExternalCouncilReviewEntity> findFirstByApplicationIdAndStatusOrderByRecordedAtDesc(UUID applicationId, String status);
}
