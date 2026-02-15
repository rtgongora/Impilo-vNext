package zw.gov.mohcc.impilo.obs.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ObsOutboxPublisherTest {

    @Test
    @DisplayName("Resolves known event types to correct topics")
    void resolvesTopics() {
        assertThat(ObsOutboxPublisher.resolveTopic("DASHBOARD_CREATED"))
                .isEqualTo("impilo.obs.dashboard.created.v1");
        assertThat(ObsOutboxPublisher.resolveTopic("ALERT_RULE_CREATED"))
                .isEqualTo("impilo.obs.alert-rule.created.v1");
        assertThat(ObsOutboxPublisher.resolveTopic("UNKNOWN"))
                .isEqualTo("impilo.obs.unknown");
    }
}
