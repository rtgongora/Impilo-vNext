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

    /**
     * Unread when the actor has never read the conversation (lastReadAt == null):
     * every message from another sender counts.
     *
     * <p>Split from the since-variant deliberately: a single query with
     * {@code (:lastReadAt IS NULL OR ...)} makes Postgres type the bound
     * parameter from an untyped {@code $n IS NULL} context, which fails with
     * {@code 42P18 "could not determine data type of parameter"} whenever
     * lastReadAt is null — i.e. for every fresh participant — 500-ing the whole
     * inbox. Two typed queries avoid the untyped-null entirely.</p>
     */
    @Query("""
            SELECT COUNT(m) FROM MessageEntity m
            WHERE m.conversationId = :conversationId
              AND m.deletedAt IS NULL
              AND m.senderId <> :actorId
            """)
    long countUnreadAll(@Param("conversationId") UUID conversationId,
                        @Param("actorId") String actorId);

    /** Unread since the actor's last-read time (non-null): messages after it, not sent by the actor. */
    @Query("""
            SELECT COUNT(m) FROM MessageEntity m
            WHERE m.conversationId = :conversationId
              AND m.deletedAt IS NULL
              AND m.senderId <> :actorId
              AND m.sentAt > :lastReadAt
            """)
    long countUnreadSince(@Param("conversationId") UUID conversationId,
                          @Param("actorId") String actorId,
                          @Param("lastReadAt") OffsetDateTime lastReadAt);

    /** Convenience: dispatches to the typed query pair based on whether lastReadAt is set. */
    default long countUnread(UUID conversationId, String actorId, OffsetDateTime lastReadAt) {
        return lastReadAt == null
                ? countUnreadAll(conversationId, actorId)
                : countUnreadSince(conversationId, actorId, lastReadAt);
    }
}
