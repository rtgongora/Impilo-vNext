package zw.gov.mohcc.impilo.channels.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.channels.domain.ChannelSessionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChannelSessionRepository extends JpaRepository<ChannelSessionEntity, UUID> {

    List<ChannelSessionEntity> findByTenantIdAndSessionState(UUID tenantId, String sessionState);
}
