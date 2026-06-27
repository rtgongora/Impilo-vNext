package zw.gov.mohcc.impilo.khuluma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationLinkEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationLinkRepository extends JpaRepository<ConversationLinkEntity, UUID> {

    List<ConversationLinkEntity> findByConversationId(UUID conversationId);

    List<ConversationLinkEntity> findByTenantIdAndObjectTypeAndObjectId(UUID tenantId, String objectType, String objectId);

    Optional<ConversationLinkEntity> findByConversationIdAndObjectTypeAndObjectId(
            UUID conversationId, String objectType, String objectId);
}
