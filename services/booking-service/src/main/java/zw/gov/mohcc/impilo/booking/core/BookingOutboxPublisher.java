package zw.gov.mohcc.impilo.booking.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.booking.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.booking.persistence.repository.EventOutboxRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class BookingOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingOutboxPublisher.class);

    private final EventOutboxRepository outboxRepository;

    public BookingOutboxPublisher(EventOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public void append(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }

    @Scheduled(fixedDelayString = "${booking.outbox.poll-interval-ms:2000}")
    @Transactional
    public void poll() {
        List<EventOutboxEntity> pending = outboxRepository.findUnpublished(PageRequest.of(0, 100));
        if (pending.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (EventOutboxEntity event : pending) {
            event.setPublishedAt(now);
            outboxRepository.save(event);
            log.debug("Marked booking outbox event published type={} aggregateId={}",
                    event.getEventType(), event.getAggregateId());
        }
        log.info("Published {} booking outbox events (deferred Kafka bridge)", pending.size());
    }
}
