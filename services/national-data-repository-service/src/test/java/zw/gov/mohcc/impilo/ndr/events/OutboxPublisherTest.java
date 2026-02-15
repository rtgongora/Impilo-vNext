package zw.gov.mohcc.impilo.ndr.events;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxPublisherTest {

    @Test
    @DisplayName("DATASET_CREATED routes to impilo.ndr.dataset.created.v1")
    void routeDatasetCreated() {
        assertThat(OutboxPublisher.routeTopic("DATASET_CREATED"))
                .isEqualTo("impilo.ndr.dataset.created.v1");
    }

    @Test
    @DisplayName("DATASET_VERSIONED routes to impilo.ndr.dataset.versioned.v1")
    void routeDatasetVersioned() {
        assertThat(OutboxPublisher.routeTopic("DATASET_VERSIONED"))
                .isEqualTo("impilo.ndr.dataset.versioned.v1");
    }

    @Test
    @DisplayName("Unknown event type routes to fallback topic")
    void routeUnknown() {
        assertThat(OutboxPublisher.routeTopic("SOME_OTHER_EVENT"))
                .isEqualTo("impilo.ndr.events");
    }
}
