package zw.gov.mohcc.impilo.channels.api.dto;

/**
 * Outcome of a notify-only send.
 *
 * @param status      DISPATCHED when an external gateway is configured, else QUEUED (recorded as an
 *                    outbox event for a downstream adapter when one becomes available)
 * @param channelType the external channel
 * @param reference   the opaque correlation token echoed back
 * @param message     the exact (content-free) template that was/will be sent
 */
public record NotifyOnlyResult(String status, String channelType, String reference, String message) {}
