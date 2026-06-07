package zw.gov.mohcc.impilo.live.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventQuestionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface LiveEventQuestionRepository extends JpaRepository<LiveEventQuestionEntity, UUID> {

    List<LiveEventQuestionEntity> findByEventIdAndStatusOrderByCreatedAtAsc(UUID eventId, String status);

    List<LiveEventQuestionEntity> findByEventIdOrderByPinnedDescUpvotesDescCreatedAtAsc(UUID eventId);
}
