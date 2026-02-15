package zw.gov.mohcc.impilo.notification.provider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProviderRegistry {

    private final Map<String, NotificationProvider> providers;
    private final NotificationProvider defaultProvider;

    private static final Map<String, String> CHANNEL_TO_PROVIDER = Map.of(
            "SMS", "sms_stub",
            "EMAIL", "email_stub"
    );

    public ProviderRegistry(List<NotificationProvider> providerList) {
        this.providers = providerList.stream()
                .collect(Collectors.toMap(NotificationProvider::name, Function.identity()));
        this.defaultProvider = providers.get("mock");
    }

    public NotificationProvider resolve(String channel) {
        String providerName = CHANNEL_TO_PROVIDER.getOrDefault(channel.toUpperCase(), "mock");
        return providers.getOrDefault(providerName, defaultProvider);
    }
}
