package zw.gov.mohcc.impilo.khuluma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.khuluma.domain.MeetingAdmissionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingAdmissionRepository extends JpaRepository<MeetingAdmissionEntity, UUID> {

    Optional<MeetingAdmissionEntity> findByConversationIdAndActorId(UUID conversationId, String actorId);

    List<MeetingAdmissionEntity> findByConversationIdOrderByRequestedAtAsc(UUID conversationId);

    List<MeetingAdmissionEntity> findByConversationIdAndStatusOrderByRequestedAtAsc(UUID conversationId, String status);

    Optional<MeetingAdmissionEntity> findByRtcSessionIdAndActorId(String rtcSessionId, String actorId);

    List<MeetingAdmissionEntity> findByRtcSessionId(String rtcSessionId);
}
