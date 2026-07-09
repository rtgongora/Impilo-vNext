package zw.gov.mohcc.impilo.notification.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.notification.provider.ProviderRegistry;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Honesty surface for channel configuration: which provider actually backs each
 * channel right now, and whether it delivers for real or only logs/stubs.
 * Read-only; never exposes credentials — only provider names and readiness.
 */
@RestController
@RequestMapping("/internal/v1/channels")
public class ChannelStatusController {

    /** Providers that really deliver to an external destination. */
    private static final Set<String> PRODUCTION_PROVIDERS = Set.of("smtp_email", "http_sms");

    private final ProviderRegistry providerRegistry;

    public ChannelStatusController(ProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        RequestContextHolder.require();
        return Map.of(
                "channels",
                List.of(
                        channelStatus("IN_APP", "inbox", true,
                                "In-app inbox rows are always persisted; no external adapter involved."),
                        describe("EMAIL"),
                        describe("SMS")));
    }

    private Map<String, Object> describe(String channel) {
        String provider = providerRegistry.resolve(channel).name();
        boolean productionReady = PRODUCTION_PROVIDERS.contains(provider);
        return channelStatus(channel, provider, productionReady,
                productionReady
                        ? "Configured for real delivery."
                        : "Stub/log provider active — messages are recorded but NOT delivered externally.");
    }

    private static Map<String, Object> channelStatus(
            String channel, String provider, boolean productionReady, String note) {
        return Map.of(
                "channel", channel,
                "activeProvider", provider,
                "productionReady", productionReady,
                "note", note);
    }
}
