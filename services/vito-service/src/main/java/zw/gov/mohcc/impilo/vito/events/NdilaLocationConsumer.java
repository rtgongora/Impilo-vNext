package zw.gov.mohcc.impilo.vito.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;

import java.util.UUID;

/**
 * Delegation back-hop (G4, Phase 0): when ndila materializes/updates the canonical location for a
 * client address VITO delegated, it emits {@code ndila.location.created/updated.v1} carrying the
 * owner back-reference + the location id. This consumer stores that id on the client as a cache
 * ({@code clients.ndila_location_id}) so VITO/BFF can resolve the canonical geography directly.
 *
 * <p>Best-effort and idempotent: only reacts to locations owned by {@code vito}/{@code client},
 * only writes when the id actually changes, and swallows/logs any failure (this is a cache hint,
 * never authoritative — ndila remains the geography SoR).</p>
 */
@Component
public class NdilaLocationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NdilaLocationConsumer.class);

    private final ClientRepository clientRepository;
    private final ObjectMapper objectMapper;

    public NdilaLocationConsumer(ClientRepository clientRepository, ObjectMapper objectMapper) {
        this.clientRepository = clientRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"ndila.location.created.v1", "ndila.location.updated.v1"},
            groupId = "vito-ndila-location"
    )
    @Transactional
    public void onLocationEvent(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            // Tolerate an outer envelope {payload:{...}} or a flat payload.
            JsonNode p = node.has("payload") && node.get("payload").isObject() ? node.get("payload") : node;

            if (!"vito".equalsIgnoreCase(p.path("ownerService").asText(""))
                    || !"client".equalsIgnoreCase(p.path("ownerEntityType").asText(""))) {
                return; // not a VITO client location — ignore
            }
            String healthIdStr = p.path("ownerEntityId").asText("");
            String tenantIdStr = p.path("tenantId").asText("");
            String locationIdStr = p.path("locationId").asText(p.path("id").asText(""));
            if (healthIdStr.isBlank() || tenantIdStr.isBlank() || locationIdStr.isBlank()) {
                return;
            }

            UUID tenantId = UUID.fromString(tenantIdStr);
            UUID healthId = UUID.fromString(healthIdStr);
            UUID locationId = UUID.fromString(locationIdStr);

            clientRepository.findByTenantIdAndHealthId(tenantId, healthId).ifPresent(client -> {
                if (!locationId.equals(client.getNdilaLocationId())) {
                    client.setNdilaLocationId(locationId);
                    clientRepository.save(client);
                    log.info("Cached ndila_location_id={} on client {}", locationId, healthId);
                }
            });
        } catch (Exception e) {
            // Cache hint only — never break on a malformed/foreign location event.
            log.warn("Failed to process ndila location event: {}", e.getMessage());
        }
    }
}
