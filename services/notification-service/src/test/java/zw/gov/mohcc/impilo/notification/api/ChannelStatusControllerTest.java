package zw.gov.mohcc.impilo.notification.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.notification.provider.NotificationProvider;
import zw.gov.mohcc.impilo.notification.provider.ProviderRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelStatusControllerTest {

    private static NotificationProvider provider(String name) {
        return new NotificationProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void send(String channel, String to, String subject, String body) {
                // status checks never dispatch
            }
        };
    }

    @BeforeEach
    void bindContext() {
        RequestContextHolder.set(new RequestContext(
                "tenant-moh-zw", "national", "req-1", "corr-1", null, null, null));
    }

    @AfterEach
    void clearContext() {
        RequestContextHolder.clear();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> channel(Map<String, Object> body, String name) {
        return ((List<Map<String, Object>>) body.get("channels")).stream()
                .filter(c -> name.equals(c.get("channel")))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void stubProvidersReportNotProductionReady() {
        ProviderRegistry registry = new ProviderRegistry(
                List.of(provider("email_log"), provider("sms_log"), provider("mock")));
        Map<String, Object> body = new ChannelStatusController(registry).status();

        assertEquals(false, channel(body, "EMAIL").get("productionReady"));
        assertEquals("email_log", channel(body, "EMAIL").get("activeProvider"));
        assertEquals(false, channel(body, "SMS").get("productionReady"));
        assertEquals(true, channel(body, "IN_APP").get("productionReady"));
    }

    @Test
    void realAdaptersReportProductionReady() {
        ProviderRegistry registry = new ProviderRegistry(
                List.of(provider("smtp_email"), provider("http_sms"), provider("mock")));
        Map<String, Object> body = new ChannelStatusController(registry).status();

        assertEquals(true, channel(body, "EMAIL").get("productionReady"));
        assertEquals("smtp_email", channel(body, "EMAIL").get("activeProvider"));
        assertEquals(true, channel(body, "SMS").get("productionReady"));
        assertEquals("http_sms", channel(body, "SMS").get("activeProvider"));
    }

    @Test
    void requiresBoundRequestContext() {
        RequestContextHolder.clear();
        ProviderRegistry registry = new ProviderRegistry(List.of(provider("mock")));
        assertThrows(IllegalStateException.class, () -> new ChannelStatusController(registry).status());
    }
}
