package zw.gov.mohcc.impilo.khuluma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationParticipantEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipantEntity, UUID> {

    List<ConversationParticipantEntity> findByConversationId(UUID conversationId);

    Optional<ConversationParticipantEntity> findByConversationIdAndActorId(UUID conversationId, String actorId);

    List<ConversationParticipantEntity> findByTenantIdAndActorIdAndActiveTrue(UUID tenantId, String actorId);

    boolean existsByConversationIdAndActorIdAndActiveTrue(UUID conversationId, String actorId);
}
