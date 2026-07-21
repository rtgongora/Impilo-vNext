package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.RatingDomainScoreEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface RatingDomainScoreRepository extends JpaRepository<RatingDomainScoreEntity, UUID> {

    List<RatingDomainScoreEntity> findByRatingId(UUID ratingId);

    List<RatingDomainScoreEntity> findByTenantIdAndDomain(UUID tenantId, String domain);
}
