package zw.gov.mohcc.impilo.channels.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.channels.domain.AssistedInteractionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssistedInteractionRepository extends JpaRepository<AssistedInteractionEntity, UUID> {

    List<AssistedInteractionEntity> findBySessionId(UUID sessionId);
}
