package zw.gov.mohcc.impilo.khuluma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.khuluma.domain.MessageEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    List<MessageEntity> findByConversationIdOrderBySentAtAsc(UUID conversationId);

    Optional<MessageEntity> findByConversationIdAndClientMessageId(UUID conversationId, String clientMessageId);

    /** Unread = messages in the conversation after the actor's last-read time, not sent by the actor. */
    @Query("""
            SELECT COUNT(m) FROM MessageEntity m
            WHERE m.conversationId = :conversationId
              AND m.deletedAt IS NULL
              AND m.senderId <> :actorId
              AND (:lastReadAt IS NULL OR m.sentAt > :lastReadAt)
            """)
    long countUnread(@Param("conversationId") UUID conversationId,
                     @Param("actorId") String actorId,
                     @Param("lastReadAt") OffsetDateTime lastReadAt);
}
