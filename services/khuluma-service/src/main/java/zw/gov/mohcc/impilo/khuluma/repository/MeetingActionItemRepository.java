package zw.gov.mohcc.impilo.khuluma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.khuluma.domain.MeetingActionItemEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeetingActionItemRepository extends JpaRepository<MeetingActionItemEntity, UUID> {

    List<MeetingActionItemEntity> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    Optional<MeetingActionItemEntity> findByActionItemIdAndTenantId(UUID actionItemId, UUID tenantId);
}
