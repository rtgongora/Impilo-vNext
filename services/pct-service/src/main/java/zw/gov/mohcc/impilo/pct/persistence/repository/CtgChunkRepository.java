package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.CtgChunkEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface CtgChunkRepository extends JpaRepository<CtgChunkEntity, UUID> {

    List<CtgChunkEntity> findBySessionIdOrderByStartedAtAsc(UUID sessionId);

    List<CtgChunkEntity> findBySessionIdAndChannelOrderByStartedAtAsc(UUID sessionId, String channel);
}
