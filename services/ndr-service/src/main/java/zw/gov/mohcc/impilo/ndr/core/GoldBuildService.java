package zw.gov.mohcc.impilo.ndr.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ndr.api.dto.GoldBuildResponse;
import zw.gov.mohcc.impilo.ndr.domain.BronzeEventEntity;
import zw.gov.mohcc.impilo.ndr.domain.GoldEncounterEntity;
import zw.gov.mohcc.impilo.ndr.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.ndr.repository.BronzeEventRepository;
import zw.gov.mohcc.impilo.ndr.repository.GoldEncounterRepository;
import zw.gov.mohcc.impilo.ndr.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class GoldBuildService {

    private static final Logger log = LoggerFactory.getLogger(GoldBuildService.class);
    private static final String PRODUCER = "ndr-service";

    private final BronzeEventRepository bronzeRepository;
    private final GoldEncounterRepository goldEncounterRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public GoldBuildService(BronzeEventRepository bronzeRepository,
                            GoldEncounterRepository goldEncounterRepository,
                            OutboxEventRepository outboxRepository,
                            ObjectMapper objectMapper) {
        this.bronzeRepository = bronzeRepository;
        this.goldEncounterRepository = goldEncounterRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GoldBuildResponse buildGoldEncounters(UUID tenantId, String podId,
                                                  String correlationId,
                                                  String idempotencyKey) {
        String buildId = UUID.randomUUID().toString();
        int created = 0;
        int updated = 0;

        // Find encounter-related bronze events
        List<BronzeEventEntity> encounterEvents = bronzeRepository
                .findByTenantIdAndEventTypeLike(tenantId, "impilo.butano.fhir.encounter.%");

        // Also include PCT encounter lifecycle events
        List<BronzeEventEntity> pctEvents = bronzeRepository
                .findByTenantIdAndEventTypeLike(tenantId, "impilo.pct.%encounter%");

        encounterEvents.addAll(pctEvents);

        for (BronzeEventEntity bronze : encounterEvents) {
            try {
                JsonNode envelope = objectMapper.readTree(bronze.getEnvelopeJson());
                JsonNode payload = envelope.get("payload");
                if (payload == null) continue;

                String subjectId = bronze.getSubjectId();
                UUID encounterId;
                try {
                    encounterId = UUID.fromString(subjectId);
                } catch (IllegalArgumentException e) {
                    encounterId = UUID.nameUUIDFromBytes(subjectId.getBytes());
                }

                Optional<GoldEncounterEntity> existing = goldEncounterRepository.findById(encounterId);

                if (existing.isPresent()) {
                    GoldEncounterEntity encounter = existing.get();
                    updateEncounterFromPayload(encounter, payload, bronze);
                    goldEncounterRepository.save(encounter);
                    updated++;
                } else {
                    GoldEncounterEntity encounter = new GoldEncounterEntity();
                    encounter.setEncounterId(encounterId);
                    encounter.setTenantId(tenantId);
                    encounter.setPatientRef(extractField(payload, "patient_ref", subjectId));
                    encounter.setFacilityRef(extractField(payload, "facility_ref", null));
                    encounter.setStatus(extractField(payload, "status", "UNKNOWN"));
                    encounter.setSourceEventId(bronze.getEventId());
                    if (bronze.getOccurredAt() != null) {
                        encounter.setStartedAt(bronze.getOccurredAt());
                    }
                    goldEncounterRepository.save(encounter);
                    created++;
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse bronze event envelope for gold build [eventId={}]",
                        bronze.getEventId(), e);
            }
        }

        // Write outbox event for the build
        Map<String, Object> buildPayload = new LinkedHashMap<>();
        buildPayload.put("build_id", buildId);
        buildPayload.put("encounters_created", created);
        buildPayload.put("encounters_updated", updated);
        buildPayload.put("total_bronze_processed", encounterEvents.size());

        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType("GoldBuild");
        outbox.setAggregateId(buildId);
        outbox.setEventType("impilo.ndr.gold.encounters.built.v1");
        outbox.setSchemaVersion("1");
        outbox.setCorrelationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setCausationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setProducer(PRODUCER);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(buildId);
        outbox.setSubjectType("GoldBuild");
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(buildPayload));
        outbox.setPartitionKey(buildId);
        outboxRepository.save(outbox);

        log.info("Gold encounters build completed [buildId={}, created={}, updated={}]",
                buildId, created, updated);

        return new GoldBuildResponse("COMPLETED", created, updated,
                buildId, OffsetDateTime.now().toString());
    }

    private void updateEncounterFromPayload(GoldEncounterEntity encounter,
                                             JsonNode payload,
                                             BronzeEventEntity bronze) {
        String status = extractField(payload, "status", null);
        if (status != null) encounter.setStatus(status);

        String facilityRef = extractField(payload, "facility_ref", null);
        if (facilityRef != null) encounter.setFacilityRef(facilityRef);

        encounter.setSourceEventId(bronze.getEventId());
        encounter.setUpdatedAt(OffsetDateTime.now());
    }

    private String extractField(JsonNode payload, String field, String defaultValue) {
        if (payload != null && payload.has(field) && !payload.get(field).isNull()) {
            return payload.get(field).asText();
        }
        return defaultValue;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new RuntimeException("JSON serialization failed", e); }
    }
}
