package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.CoachingTouchpointEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CoachingTouchpointRepository extends JpaRepository<CoachingTouchpointEntity, Long> {

    List<CoachingTouchpointEntity> findByRelationshipIdOrderByCreatedAtDesc(UUID relationshipId);
}
