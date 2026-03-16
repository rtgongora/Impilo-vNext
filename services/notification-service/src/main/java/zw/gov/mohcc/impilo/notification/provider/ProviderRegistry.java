package zw.gov.mohcc.impilo.notification.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry that resolves notification channels to their configured providers.
 * <p>
 * Providers are auto-discovered via Spring component scanning. The registry
 * maps channels (SMS, EMAIL, PUSH) to the active provider bean. Which provider
 * is active for each channel depends on the {@code notification.email.provider}
 * and {@code notification.sms.provider} configuration properties.
 */
@Component
public class ProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProviderRegistry.class);

    private final Map<String, NotificationProvider> providers;

    public ProviderRegistry(List<NotificationProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(NotificationProvider::name, Function.identity()));
        log.info("Notification providers registered: {}", providers.keySet());
    }

    /**
     * Resolves the provider for the given channel. Checks for production providers
     * first, then falls back to dev/log providers, then to the mock provider.
     */
    public NotificationProvider resolve(String channel) {
        String upperChannel = channel.toUpperCase();
        return switch (upperChannel) {
            case "EMAIL" -> resolveFirst("smtp_email", "email_log", "mock");
            case "SMS" -> resolveFirst("http_sms", "sms_log", "mock");
            default -> resolveFirst("mock");
        };
    }

    private NotificationProvider resolveFirst(String... names) {
        for (String name : names) {
            NotificationProvider provider = providers.get(name);
            if (provider != null) {
                return provider;
            }
        }
        log.warn("No provider found for candidates {}, using first available", List.of(names));
        return providers.values().iterator().next();
    }
}
