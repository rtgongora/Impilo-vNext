package zw.gov.mohcc.impilo.channels.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.channels.api.dto.NotifyOnlyResult;
import zw.gov.mohcc.impilo.companion.context.RequestContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * External <b>notify-only</b> dispatch: emit a content-free prompt over an external channel
 * (SMS/EMAIL/WHATSAPP) so a recipient logs in to view something securely — never carrying clinical
 * detail (the message is always a fixed template; the API has no content field).
 *
 * <p>Channels-service records the intent as an outbox event for a downstream gateway adapter to
 * deliver. Honest configured/not-configured: when no external gateway is configured
 * ({@code impilo.channels.external-notify.enabled=false}) the result is {@code QUEUED} rather than
 * {@code DISPATCHED}.</p>
 */
@Service
public class NotifyOnlyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyOnlyService.class);

    private final OutboxService outboxService;
    private final boolean externalNotifyEnabled;
    private final String template;

    public NotifyOnlyService(
            OutboxService outboxService,
            @Value("${impilo.channels.external-notify.enabled:false}") boolean externalNotifyEnabled,
            @Value("${impilo.channels.external-notify.template:You have a secure health notification. Please log in to Impilo to view it.}") String template) {
        this.outboxService = outboxService;
        this.externalNotifyEnabled = externalNotifyEnabled;
        this.template = template;
    }

    @Transactional
    public NotifyOnlyResult send(RequestContext ctx, String channelType, String recipient,
                                 String reference, String idempotencyKey) {
        // Content is ALWAYS the fixed template — caller content is never accepted, so no clinical
        // detail can travel over the external channel.
        String message = template;
        String status = externalNotifyEnabled ? "DISPATCHED" : "QUEUED";
        String aggregateId = reference != null && !reference.isBlank() ? reference : UUID.randomUUID().toString();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel_type", channelType);
        payload.put("recipient", recipient);
        payload.put("reference", reference);
        payload.put("message", message);
        payload.put("notify_only", true);
        payload.put("status", status);

        outboxService.persistEvent(ctx,
                "impilo.channels.notify_only.outbound.v1",
                "NotifyOnly", aggregateId, "NotifyOnly", aggregateId,
                idempotencyKey + ":notify_only", payload);

        log.info("Notify-only {}: channel={}, reference={}", status, channelType, reference);
        return new NotifyOnlyResult(status, channelType, reference, message);
    }
}
