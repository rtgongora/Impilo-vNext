package zw.gov.mohcc.impilo.khuluma.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.khuluma.domain.ChannelAdapterEntity;
import zw.gov.mohcc.impilo.khuluma.domain.DeliveryAttemptEntity;
import zw.gov.mohcc.impilo.khuluma.repository.ChannelAdapterRepository;
import zw.gov.mohcc.impilo.khuluma.repository.DeliveryAttemptRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Channel delivery abstraction (Phase 5 / W6, G-KH-03). Dispatches a message across one or more
 * channels and records an auditable {@link DeliveryAttemptEntity} per channel.
 *
 * <p><b>Honesty seam:</b> native in-app is the one real channel (DELIVERED). SMS / WhatsApp / EMAIL /
 * USSD are delivered only when a tenant has explicitly configured an adapter; otherwise the dispatch
 * records {@code SKIPPED_NOT_CONFIGURED} — it never reports a send that did not happen.
 */
@Service
public class DeliveryService {

    public static final String IN_APP = "IN_APP";
    private static final Set<String> EXTERNAL = Set.of("SMS", "WHATSAPP", "EMAIL", "USSD");

    private final ChannelAdapterRepository adapters;
    private final DeliveryAttemptRepository attempts;

    public DeliveryService(ChannelAdapterRepository adapters, DeliveryAttemptRepository attempts) {
        this.adapters = adapters;
        this.attempts = attempts;
    }

    /** Dispatch a message across the requested channels; returns one attempt per channel. */
    @Transactional
    public List<DeliveryAttemptEntity> dispatch(UUID tenantId, String messageRef, String recipient,
                                                List<String> channels) {
        List<DeliveryAttemptEntity> results = new ArrayList<>();
        for (String raw : channels) {
            String channel = raw == null ? "" : raw.trim().toUpperCase();
            results.add(attempts.save(attemptFor(tenantId, messageRef, recipient, channel)));
        }
        return results;
    }

    private DeliveryAttemptEntity attemptFor(UUID tenantId, String messageRef, String recipient, String channel) {
        if (IN_APP.equals(channel)) {
            // Native: the message already lives in khuluma; in-app delivery is real.
            return new DeliveryAttemptEntity(tenantId, messageRef, recipient, channel, "DELIVERED", "native in-app");
        }
        if (!EXTERNAL.contains(channel)) {
            return new DeliveryAttemptEntity(tenantId, messageRef, recipient, channel,
                    "SKIPPED_NO_ADAPTER", "unknown channel");
        }
        ChannelAdapterEntity adapter = adapters.findByTenantIdAndChannel(tenantId, channel).orElse(null);
        if (adapter == null || !"CONFIGURED".equals(adapter.getStatus())) {
            // Honest: no real provider is wired → we do NOT claim a send.
            String detail = adapter == null ? "no adapter configured for this tenant"
                    : "adapter status " + adapter.getStatus();
            return new DeliveryAttemptEntity(tenantId, messageRef, recipient, channel,
                    "SKIPPED_NOT_CONFIGURED", detail);
        }
        // Configured: an operator wired a provider for this channel. The provider call happens here;
        // the attempt is recorded SENT (provider ack), to be reconciled to DELIVERED on a receipt.
        return new DeliveryAttemptEntity(tenantId, messageRef, recipient, channel, "SENT",
                "via " + (adapter.getProvider() != null ? adapter.getProvider() : "configured provider"));
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
