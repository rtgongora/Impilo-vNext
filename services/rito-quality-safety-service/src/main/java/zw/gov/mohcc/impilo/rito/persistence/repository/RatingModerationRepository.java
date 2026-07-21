package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.RatingModerationEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface RatingModerationRepository extends JpaRepository<RatingModerationEntity, UUID> {

    List<RatingModerationEntity> findByRatingId(UUID ratingId);

    List<RatingModerationEntity> findByTenantIdAndState(UUID tenantId, String state);
}
