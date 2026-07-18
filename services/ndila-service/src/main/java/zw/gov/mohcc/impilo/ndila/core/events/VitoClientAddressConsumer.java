package zw.gov.mohcc.impilo.ndila.core.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.ndila.core.location.NdilaLocationService;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;

import java.util.UUID;

/**
 * G4 address/geography delegation (Phase 0): materializes the canonical ndila location for a client
 * address that VITO delegates. VITO emits {@code CLIENT_ADDRESS_UPSERTED} on the shared
 * {@code vito.identity} / {@code impilo.vito.identity} stream; this consumer upserts an owner-keyed
 * {@code ndila_locations} row (owner = {@code vito}/{@code client}/{@code healthId}) so the geography
 * SoR — not VITO — owns the normalized, geocodable location. Idempotent + poison-safe.
 */
@Component
public class VitoClientAddressConsumer {

    private static final Logger log = LoggerFactory.getLogger(VitoClientAddressConsumer.class);
    private static final String EVENT_TYPE = "CLIENT_ADDRESS_UPSERTED";

    private final NdilaLocationService locationService;
    private final ObjectMapper objectMapper;

    public VitoClientAddressConsumer(NdilaLocationService locationService, ObjectMapper objectMapper) {
        this.locationService = locationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {"vito.identity", "impilo.vito.identity"},
            groupId = "ndila-vito-address"
    )
    public void onVitoIdentityEvent(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            // Defensive: a mis-configured producer (JsonSerializer over a pre-serialized
            // JSON string) puts a quoted string literal on the wire — unwrap it so the
            // event isn't silently dropped as a non-matching type.
            if (node != null && node.isTextual()) {
                node = objectMapper.readTree(node.asText());
            }
            // Tolerate a flat payload or an outer {payload:{...}} envelope.
            JsonNode body = node.has("payload") && node.get("payload").isObject() ? node.get("payload") : node;

            String eventType = firstNonBlank(
                    body.path("eventType").asText(""),
                    node.path("eventType").asText(""),
                    node.path("event_type").asText(""));
            if (!EVENT_TYPE.equals(eventType)) {
                return; // not an address event — ignore the rest of the vito.identity stream
            }

            String healthId = body.path("healthId").asText("");
            String tenantId = body.path("tenantId").asText("");
            JsonNode address = body.path("address");
            if (healthId.isBlank() || tenantId.isBlank() || address.isMissingNode() || !address.isObject()) {
                log.debug("Skipping CLIENT_ADDRESS_UPSERTED missing healthId/tenantId/address");
                return;
            }

            String addressLine1 = text(address, "addressLine1");
            String city = text(address, "city");
            String district = text(address, "district");
            String province = text(address, "province");

            NdilaLocationService.CreateLocation cmd = new NdilaLocationService.CreateLocation(
                    UUID.fromString(tenantId),
                    "vito",
                    "client",
                    healthId,
                    "Client residence " + healthId,
                    "Residential address delegated from VITO client registry",
                    "RESIDENCE",
                    null, null, null,          // latitude, longitude, altitude — geocoded later by ndila
                    addressLine1,
                    city,                       // locality
                    null,                       // ward (not carried by vito address)
                    district,
                    province,
                    "ZWE",
                    "DELEGATED_VITO",
                    "PRIVATE",                  // a person's home address
                    null);

            NdilaLocationEntity entity = locationService.upsertByOwner(cmd, UUID.randomUUID());
            log.info("Materialized ndila location {} for VITO client {}", entity.getId(), healthId);
        } catch (Exception e) {
            // Poison-safe: a malformed/foreign event must not stall the shared vito.identity stream.
            log.warn("Failed to process vito client-address event: {}", e.getMessage());
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }
}
