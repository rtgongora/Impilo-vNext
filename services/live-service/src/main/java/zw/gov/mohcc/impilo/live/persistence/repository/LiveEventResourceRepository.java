package zw.gov.mohcc.impilo.live.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventResourceEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface LiveEventResourceRepository extends JpaRepository<LiveEventResourceEntity, UUID> {

    List<LiveEventResourceEntity> findByEventIdOrderByCreatedAtAsc(UUID eventId);
}
