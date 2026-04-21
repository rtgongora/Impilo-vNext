package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.CommitteeReviewEntity;

import java.util.List;
import java.util.UUID;

public interface CommitteeReviewRepository extends JpaRepository<CommitteeReviewEntity, UUID> {
    List<CommitteeReviewEntity> findByFacilityIdOrderByDecidedAtDesc(Long facilityId);
    List<CommitteeReviewEntity> findByApplicationApplicationIdOrderByDecidedAtDesc(UUID applicationId);
}
