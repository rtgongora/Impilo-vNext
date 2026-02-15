package zw.gov.mohcc.impilo.secharden.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecHardenOutboxPublisherTest {

    @Test
    @DisplayName("Resolves known event types to correct topics")
    void resolvesTopics() {
        assertThat(SecHardenOutboxPublisher.resolveTopic("PACK_CREATED"))
                .isEqualTo("impilo.secharden.pack.created.v1");
        assertThat(SecHardenOutboxPublisher.resolveTopic("SCAN_COMPLETED"))
                .isEqualTo("impilo.secharden.scan.completed.v1");
        assertThat(SecHardenOutboxPublisher.resolveTopic("UNKNOWN"))
                .isEqualTo("impilo.secharden.unknown");
    }
}
