package zw.gov.mohcc.impilo.khuluma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.khuluma.domain.ConversationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    Optional<ConversationEntity> findByConversationIdAndTenantId(UUID conversationId, UUID tenantId);

    /** Channel/community discovery: active channels for a scope (e.g. a facility's channels). */
    List<ConversationEntity> findByTenantIdAndScopeTypeAndScopeRefAndStatusOrderByCreatedAtDesc(
            UUID tenantId, String scopeType, UUID scopeRef, String status);
}
