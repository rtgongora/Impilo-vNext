package zw.gov.mohcc.impilo.khuluma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.khuluma.domain.CallEventEntity;

import java.util.List;
import java.util.UUID;

public interface CallEventRepository extends JpaRepository<CallEventEntity, UUID> {

    List<CallEventEntity> findByCallIdOrderByOccurredAtAsc(UUID callId);
}
