package zw.gov.mohcc.impilo.vito.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Emits a {@code CLIENT_ADDRESS_UPSERTED} outbox event when a client's address changes, so the
 * ndila geography SoR can materialize/geocode the canonical location (G4 delegation, Phase 0 seam).
 *
 * <p><b>Best-effort and additive:</b> this never changes or blocks client registration/update — the
 * address is still written to {@code clients.address} exactly as before, and a failure to emit is
 * swallowed (logged). It reuses the established {@code CLIENT} aggregate → {@code vito.identity} /
 * {@code impilo.vito.identity} topics (the same stream mushe already consumes for CLIENT_CREATED),
 * so ndila subscribes there and filters for this event type.</p>
 */
@Component
public class ClientAddressDelegationPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClientAddressDelegationPublisher.class);
    private static final String EVENT_TYPE = "CLIENT_ADDRESS_UPSERTED";

    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ClientAddressDelegationPublisher(EventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Emit the address-upserted event for a persisted client. Reads the address off the client (so the
     * caller need only pass the saved entity), and no-ops when the client has no health id or no
     * meaningful address. Never throws.
     */
    public void emitAddressUpserted(ClientEntity client) {
        if (client == null || client.getHealthId() == null) {
            return;
        }
        String addressJson = client.getAddress();
        if (addressJson == null || addressJson.isBlank() || "{}".equals(addressJson.trim())) {
            return; // nothing to delegate
        }
        try {
            JsonNode address = objectMapper.readTree(addressJson);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventType", EVENT_TYPE);
            payload.put("healthId", client.getHealthId().toString());
            if (client.getTenantId() != null) {
                payload.put("tenantId", client.getTenantId().toString());
            }
            payload.put("address", address);

            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("CLIENT");
            event.setAggregateId(client.getHealthId().toString());
            event.setEventType(EVENT_TYPE);
            event.setPayload(objectMapper.writeValueAsString(payload));
            if (client.getTenantId() != null) {
                event.setTenantId(client.getTenantId().toString());
            }
            outboxRepository.save(event);
        } catch (Exception e) {
            // Best-effort: the geography-delegation seam must never fail a client registration/update.
            log.warn("Failed to emit {} for client {}: {}", EVENT_TYPE, client.getHealthId(), e.getMessage());
        }
    }
}
