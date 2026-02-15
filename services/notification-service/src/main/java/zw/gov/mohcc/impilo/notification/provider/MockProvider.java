package zw.gov.mohcc.impilo.notification.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(MockProvider.class);

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public void send(String channel, String to, String subject, String body) {
        log.info("[MOCK] channel={} to={} subject={} body_length={}", channel, to, subject,
                body != null ? body.length() : 0);
    }
}
