package zw.gov.mohcc.impilo.khuluma.core;

import zw.gov.mohcc.impilo.khuluma.domain.ConversationEntity;

/**
 * Inbox-row projection: a conversation plus the requesting actor's membership view
 * (their role, whether muted, and their unread count).
 */
public record ConversationSummary(
        ConversationEntity conversation,
        String role,
        boolean muted,
        long unreadCount
) {}
