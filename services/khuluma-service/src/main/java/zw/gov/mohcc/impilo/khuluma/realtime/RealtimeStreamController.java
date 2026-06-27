package zw.gov.mohcc.impilo.khuluma.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import zw.gov.mohcc.impilo.khuluma.core.ActorContext;
import zw.gov.mohcc.impilo.khuluma.core.ActorContextResolver;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-Sent-Events transport of the realtime gateway. A client opens one long-lived stream and
 * receives every message/receipt/presence/ringing/typing event for the channels it is entitled to
 * (its conversations + personal + tenant). Mirrors the {@code FetalMonitoringStreamService} SSE
 * precedent; subscription/dispatch is shared with the WebSocket transport via {@link RealtimeHub}.
 */
@RestController
@RequestMapping("/internal/v1/khuluma/stream")
public class RealtimeStreamController {

    private static final Logger log = LoggerFactory.getLogger(RealtimeStreamController.class);
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 min; client reconnects

    private final ActorContextResolver contextResolver;
    private final SubscriptionChannelResolver channelResolver;
    private final RealtimeHub hub;

    public RealtimeStreamController(ActorContextResolver contextResolver,
                                    SubscriptionChannelResolver channelResolver,
                                    RealtimeHub hub) {
        this.contextResolver = contextResolver;
        this.channelResolver = channelResolver;
        this.hub = hub;
    }

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sse(@RequestParam(value = "channels", required = false) String channels) {
        ActorContext ctx = contextResolver.resolve();
        Set<String> subscribed = channelResolver.resolveFor(ctx.tenantId(), ctx.actorId(), channels);
        String subscriptionId = UUID.randomUUID().toString();

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> hub.unregister(subscriptionId));
        emitter.onTimeout(() -> { hub.unregister(subscriptionId); emitter.complete(); });
        emitter.onError(err -> hub.unregister(subscriptionId));

        RealtimeSubscription.Sink sink = event ->
                emitter.send(SseEmitter.event().name(event.eventType()).data(frame(event)));
        hub.register(new RealtimeSubscription(subscriptionId, ctx.tenantId(), ctx.actorId(), subscribed, sink));

        try {
            emitter.send(SseEmitter.event().name("stream.ready")
                    .data(Map.of("actor_id", ctx.actorId(), "channels", subscribed)));
        } catch (IOException ex) {
            hub.unregister(subscriptionId);
            emitter.completeWithError(ex);
        }
        log.debug("SSE stream opened [actor={}, channels={}]", ctx.actorId(), subscribed.size());
        return emitter;
    }

    /** Wire frame: payload augmented with routing metadata the client can dispatch on. */
    static Map<String, Object> frame(RealtimeEvent event) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("event_type", event.eventType());
        frame.put("channel", event.channel());
        if (event.payload() != null) {
            frame.putAll(event.payload());
        }
        return frame;
    }
}
