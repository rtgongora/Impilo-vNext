package zw.gov.mohcc.impilo.khuluma.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.khuluma.client.NotificationDeliveryClient;
import zw.gov.mohcc.impilo.khuluma.domain.ChannelAdapterEntity;
import zw.gov.mohcc.impilo.khuluma.domain.DeliveryAttemptEntity;
import zw.gov.mohcc.impilo.khuluma.repository.ChannelAdapterRepository;
import zw.gov.mohcc.impilo.khuluma.repository.DeliveryAttemptRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Channel delivery abstraction (Phase 5 / W6, G-KH-03).  Dispatches a message across one or more
 * channels and records an auditable {@link DeliveryAttemptEntity} per channel.
 *
 * <p><b>Delivery SoR (G31):</b> native IN_APP is khuluma's own real channel (DELIVERED). External
 * channels (SMS / WhatsApp / EMAIL / USSD) are <b>delegated to notification-service</b> (the platform
 * delivery/providers system-of-record) — khuluma no longer owns provider adapters. A delegated send
 * is recorded {@code SENT} on notification-service acceptance, {@code FAILED} on error, and
 * {@code SKIPPED_NO_TEMPLATE} when no {@code templateKey} is supplied (notification-service is
 * template-driven) — it never reports a send that did not happen. The per-tenant
 * {@link ChannelAdapterEntity} rows are retained as legacy/advisory config only, not a delivery gate.
 */
@Service
public class DeliveryService {

    public static final String IN_APP = "IN_APP";
    private static final Set<String> EXTERNAL = Set.of("SMS", "WHATSAPP", "EMAIL", "USSD");

    private final ChannelAdapterRepository adapters;
    private final DeliveryAttemptRepository attempts;
    private final NotificationDeliveryClient notification;

    public DeliveryService(ChannelAdapterRepository adapters, DeliveryAttemptRepository attempts,
                           NotificationDeliveryClient notification) {
        this.adapters = adapters;
        this.attempts = attempts;
        this.notification = notification;
    }

    /** Optional content for a delegated external send (notification-service is template-driven). */
    public record DispatchContent(String templateKey, Map<String, String> variables,
                                  String patientRef, String messageKind) {
        public static DispatchContent none() { return new DispatchContent(null, null, null, null); }
    }

    /** Dispatch a message across the requested channels; returns one attempt per channel. */
    @Transactional
    public List<DeliveryAttemptEntity> dispatch(UUID tenantId, String messageRef, String recipient,
                                                List<String> channels) {
        return dispatch(tenantId, messageRef, recipient, channels, DispatchContent.none());
    }

    /** Dispatch with optional content for external channels delegated to notification-service. */
    @Transactional
    public List<DeliveryAttemptEntity> dispatch(UUID tenantId, String messageRef, String recipient,
                                                List<String> channels, DispatchContent content) {
        List<DeliveryAttemptEntity> results = new ArrayList<>();
        for (String raw : channels) {
            String channel = raw == null ? "" : raw.trim().toUpperCase();
            results.add(attempts.save(attemptFor(tenantId, messageRef, recipient, channel,
                    content != null ? content : DispatchContent.none())));
        }
        return results;
    }

    private DeliveryAttemptEntity attemptFor(UUID tenantId, String messageRef, String recipient,
                                             String channel, DispatchContent content) {
        if (IN_APP.equals(channel)) {
            // Native: the message already lives in khuluma; in-app delivery is real.
            return new DeliveryAttemptEntity(tenantId, messageRef, recipient, channel, "DELIVERED", "native in-app");
        }
        if (!EXTERNAL.contains(channel)) {
            return new DeliveryAttemptEntity(tenantId, messageRef, recipient, channel,
                    "SKIPPED_NO_ADAPTER", "unknown channel");
        }
        // G31: external delivery is notification-service's job. Without a templateKey we cannot form a
        // valid (template-driven) notify request, so we skip honestly rather than fabricate a send.
        if (content == null || content.templateKey() == null || content.templateKey().isBlank()) {
            return new DeliveryAttemptEntity(tenantId, messageRef, recipient, channel,
                    "SKIPPED_NO_TEMPLATE", "no templateKey — delegate to notification-service requires one");
        }
        NotificationDeliveryClient.DeliveryResult r = notification.deliver(
                channel, recipient, content.templateKey(), content.variables(),
                content.patientRef(), content.messageKind());
        if (r.delivered()) {
            return new DeliveryAttemptEntity(tenantId, messageRef, recipient, channel, "SENT",
                    "via notification-service" + (r.reference() != null ? " (" + r.reference() + ")" : ""));
        }
        return new DeliveryAttemptEntity(tenantId, messageRef, recipient, channel, "FAILED",
                "notification-service: " + (r.error() != null ? r.error() : "delivery rejected"));
    }

    /** Configure (or update) a tenant's adapter for an external channel. */
    @Transactional
    public ChannelAdapterEntity configureAdapter(UUID tenantId, String channel, String status, String provider) {
        String ch = channel == null ? "" : channel.trim().toUpperCase();
        if (!EXTERNAL.contains(ch) && !IN_APP.equals(ch)) {
            throw new IllegalArgumentException("Unknown channel: " + channel);
        }
        ChannelAdapterEntity a = adapters.findByTenantIdAndChannel(tenantId, ch).orElseGet(ChannelAdapterEntity::new);
        a.setTenantId(tenantId);
        a.setChannel(ch);
        a.setStatus(normalizeStatus(status));
        a.setProvider(provider);
        return adapters.save(a);
    }

    @Transactional(readOnly = true)
    public List<ChannelAdapterEntity> listAdapters(UUID tenantId) {
        return adapters.findByTenantIdOrderByChannelAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public List<DeliveryAttemptEntity> attemptsFor(UUID tenantId, String messageRef) {
        return attempts.findByTenantIdAndMessageRefOrderByAttemptedAtDesc(tenantId, messageRef);
    }

    private static String normalizeStatus(String status) {
        if (status == null) return "NOT_CONFIGURED";
        String s = status.trim().toUpperCase();
        return Set.of("CONFIGURED", "NOT_CONFIGURED", "DISABLED").contains(s) ? s : "NOT_CONFIGURED";
    }
}
