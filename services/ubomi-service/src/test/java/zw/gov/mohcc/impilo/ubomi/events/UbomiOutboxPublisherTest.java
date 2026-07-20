package zw.gov.mohcc.impilo.ubomi.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import zw.gov.mohcc.impilo.ubomi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ubomi.persistence.repository.EventOutboxRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * W1a — proves the previously-missing publisher actually drains unpublished
 * {@code ubomi.event_outbox} rows and marks them published. Uses LEGACY_ONLY so
 * topic mapping is asserted deterministically (one send per row).
 */
@ExtendWith(MockitoExtension.class)
class UbomiOutboxPublisherTest {

    @Mock EventOutboxRepository repo;
    @Mock @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka;

    private EventOutboxEntity row(long id, String eventType, String payload) {
        EventOutboxEntity e = new EventOutboxEntity();
        e.setId(id);
        e.setAggregateType("BIRTH_NOTIFICATION");
        e.setAggregateId(String.valueOf(id));
        e.setEventType(eventType);
        e.setPayload(payload);
        return e;
    }

    @Test
    void drains_unpublished_rows_and_marks_published() {
        EventOutboxEntity r1 = row(1L, "BIRTH_REGISTERED", "{\"a\":1}");
        EventOutboxEntity r2 = row(2L, "CIVIL_IDENTITY_VERIFIED", "{\"status\":\"VERIFIED\"}");
        when(repo.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(r1, r2));
        when(repo.findById(1L)).thenReturn(Optional.of(r1));
        when(repo.findById(2L)).thenReturn(Optional.of(r2));

        UbomiOutboxPublisher publisher = new UbomiOutboxPublisher(repo, kafka, "LEGACY_ONLY");
        int published = publisher.publishPendingEvents();

        assertThat(published).isEqualTo(2);

        ArgumentCaptor<String> topics = ArgumentCaptor.forClass(String.class);
        verify(kafka, times(2)).send(topics.capture(), anyString(), anyString());
        assertThat(topics.getAllValues())
                .containsExactly("ubomi.birth.registered", "civil.identity.verified");

        // Both rows marked published (published_at set).
        assertThat(r1.getPublishedAt()).isNotNull();
        assertThat(r2.getPublishedAt()).isNotNull();
        verify(repo, times(2)).save(any(EventOutboxEntity.class));
    }

    @Test
    void no_rows_is_a_noop() {
        when(repo.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of());
        UbomiOutboxPublisher publisher = new UbomiOutboxPublisher(repo, kafka, "LEGACY_ONLY");
        assertThat(publisher.publishPendingEvents()).isZero();
        verifyNoInteractions(kafka);
    }
}
