package zw.gov.mohcc.impilo.live.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventSessionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LiveEventSessionRepository extends JpaRepository<LiveEventSessionEntity, UUID> {

    List<LiveEventSessionEntity> findByEventIdOrderByCreatedAtDesc(UUID eventId);

    Optional<LiveEventSessionEntity> findFirstByEventIdAndEndedAtIsNullOrderByCreatedAtDesc(UUID eventId);

    /** Media-truth resolution: rtc-gateway events carry the rtc session id we stored as provider_room_id. */
    Optional<LiveEventSessionEntity> findFirstByProviderRoomIdOrderByCreatedAtDesc(String providerRoomId);
}
