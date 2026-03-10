package zw.gov.mohcc.impilo.channels.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.channels.domain.ChannelMessageEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChannelMessageRepository extends JpaRepository<ChannelMessageEntity, UUID> {

    List<ChannelMessageEntity> findBySessionId(UUID sessionId);
}
