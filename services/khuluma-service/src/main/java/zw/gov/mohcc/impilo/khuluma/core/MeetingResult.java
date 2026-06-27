package zw.gov.mohcc.impilo.khuluma.core;

/**
 * Outcome of creating a meeting: the backing Khuluma conversation + the live-service event handle.
 * {@code available=false} means the conversation exists but live-service could not provision the
 * event (degraded honestly).
 */
public record MeetingResult(
        String conversationId,
        String eventId,
        boolean available,
        String error
) {}
