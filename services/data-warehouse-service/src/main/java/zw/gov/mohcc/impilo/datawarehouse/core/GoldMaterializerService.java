package zw.gov.mohcc.impilo.datawarehouse.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.datawarehouse.domain.*;
import zw.gov.mohcc.impilo.datawarehouse.repository.*;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class GoldMaterializerService {

    private static final Logger log = LoggerFactory.getLogger(GoldMaterializerService.class);
    private static final String SOURCE_TABLE = "din_bronze_event";
    private static final String PRODUCER = "data-warehouse-service";

    private final GoldEncounterRepository encounterRepository;
    private final GoldMedicationRepository medicationRepository;
    private final GoldLabRepository labRepository;
    private final MaterializerWatermarkRepository watermarkRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${warehouse.materializer.batch-size:500}")
    private int batchSize;

    public GoldMaterializerService(GoldEncounterRepository encounterRepository,
                                     GoldMedicationRepository medicationRepository,
                                     GoldLabRepository labRepository,
                                     MaterializerWatermarkRepository watermarkRepository,
                                     OutboxEventRepository outboxRepository,
                                     ObjectMapper objectMapper) {
        this.encounterRepository = encounterRepository;
        this.medicationRepository = medicationRepository;
        this.labRepository = labRepository;
        this.watermarkRepository = watermarkRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Materialize a single bronze event JSON into gold tables.
     * Called by the controller for on-demand materialization.
     * Returns: number of gold records upserted (0 if event type not mapped).
     */
    @Transactional
    public int materializeEvent(String envelopeJson, UUID tenantId, String eventId, String eventType) {
        try {
            JsonNode envelope = objectMapper.readTree(envelopeJson);
            JsonNode data = envelope.has("data") ? envelope.get("data") : envelope;

            if (eventType.contains("encounter")) {
                return upsertEncounter(data, tenantId, eventId, eventType);
            } else if (eventType.contains("medication") || eventType.contains("prescription") || eventType.contains("dispense")) {
                return upsertMedication(data, tenantId, eventId, eventType);
            } else if (eventType.contains("lab") || eventType.contains("observation") || eventType.contains("diagnostic")) {
                return upsertLab(data, tenantId, eventId, eventType);
            }

            return 0;
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse envelope JSON for materialization [eventId={}]: {}", eventId, e.getMessage());
            return 0;
        }
    }

    private int upsertEncounter(JsonNode data, UUID tenantId, String eventId, String eventType) {
        String encounterId = textOrDefault(data, "encounter_id", eventId);

        GoldEncounterEntity entity = encounterRepository.findByTenantIdAndEncounterId(tenantId, encounterId)
                .orElseGet(GoldEncounterEntity::new);

        entity.setTenantId(tenantId);
        entity.setEncounterId(encounterId);
        entity.setPatientId(textOrDefault(data, "patient_id", "unknown"));
        entity.setFacilityId(textOrNull(data, "facility_id"));
        entity.setEncounterType(textOrNull(data, "encounter_type"));
        entity.setEncounterStatus(textOrNull(data, "status"));
        entity.setStartDate(parseDateTime(textOrNull(data, "start_date")));
        entity.setEndDate(parseDateTime(textOrNull(data, "end_date")));
        entity.setProviderId(textOrNull(data, "provider_id"));
        entity.setDiagnosisCodes(textOrNull(data, "diagnosis_codes"));
        entity.setSourceEventId(eventId);
        entity.setSourceEventType(eventType);
        entity.setMaterializedAt(OffsetDateTime.now());

        encounterRepository.save(entity);
        return 1;
    }

    private int upsertMedication(JsonNode data, UUID tenantId, String eventId, String eventType) {
        String medicationId = textOrDefault(data, "medication_id", eventId);

        GoldMedicationEntity entity = medicationRepository.findByTenantIdAndMedicationId(tenantId, medicationId)
                .orElseGet(GoldMedicationEntity::new);

        entity.setTenantId(tenantId);
        entity.setMedicationId(medicationId);
        entity.setPatientId(textOrDefault(data, "patient_id", "unknown"));
        entity.setEncounterId(textOrNull(data, "encounter_id"));
        entity.setFacilityId(textOrNull(data, "facility_id"));
        entity.setDrugCode(textOrNull(data, "drug_code"));
        entity.setDrugName(textOrNull(data, "drug_name"));
        entity.setDosage(textOrNull(data, "dosage"));
        entity.setFrequency(textOrNull(data, "frequency"));
        entity.setRoute(textOrNull(data, "route"));
        entity.setPrescribedDate(parseDateTime(textOrNull(data, "prescribed_date")));
        entity.setDispensedDate(parseDateTime(textOrNull(data, "dispensed_date")));
        entity.setPrescriberId(textOrNull(data, "prescriber_id"));
        entity.setSourceEventId(eventId);
        entity.setSourceEventType(eventType);
        entity.setMaterializedAt(OffsetDateTime.now());

        medicationRepository.save(entity);
        return 1;
    }

    private int upsertLab(JsonNode data, UUID tenantId, String eventId, String eventType) {
        String labResultId = textOrDefault(data, "lab_result_id", eventId);

        GoldLabEntity entity = labRepository.findByTenantIdAndLabResultId(tenantId, labResultId)
                .orElseGet(GoldLabEntity::new);

        entity.setTenantId(tenantId);
        entity.setLabResultId(labResultId);
        entity.setPatientId(textOrDefault(data, "patient_id", "unknown"));
        entity.setEncounterId(textOrNull(data, "encounter_id"));
        entity.setFacilityId(textOrNull(data, "facility_id"));
        entity.setTestCode(textOrNull(data, "test_code"));
        entity.setTestName(textOrNull(data, "test_name"));
        entity.setResultValue(textOrNull(data, "result_value"));
        entity.setResultUnit(textOrNull(data, "result_unit"));
        entity.setReferenceRange(textOrNull(data, "reference_range"));
        entity.setResultStatus(textOrNull(data, "result_status"));
        entity.setCollectedDate(parseDateTime(textOrNull(data, "collected_date")));
        entity.setReportedDate(parseDateTime(textOrNull(data, "reported_date")));
        entity.setSourceEventId(eventId);
        entity.setSourceEventType(eventType);
        entity.setMaterializedAt(OffsetDateTime.now());

        labRepository.save(entity);
        return 1;
    }

    public MaterializerWatermarkEntity getWatermark() {
        return watermarkRepository.findBySourceTable(SOURCE_TABLE).orElse(null);
    }

    @Transactional
    public void advanceWatermark(UUID lastReceiptId, OffsetDateTime lastStoredAt, long count) {
        MaterializerWatermarkEntity wm = watermarkRepository.findBySourceTable(SOURCE_TABLE)
                .orElseGet(() -> {
                    MaterializerWatermarkEntity e = new MaterializerWatermarkEntity();
                    e.setSourceTable(SOURCE_TABLE);
                    return e;
                });
        wm.setLastReceiptId(lastReceiptId);
        wm.setLastStoredAt(lastStoredAt);
        wm.setProcessedCount(wm.getProcessedCount() + count);
        wm.setUpdatedAt(OffsetDateTime.now());
        watermarkRepository.save(wm);
    }

    public long countEncounters() { return encounterRepository.count(); }
    public long countMedications() { return medicationRepository.count(); }
    public long countLabs() { return labRepository.count(); }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("gold_encounters", countEncounters());
        stats.put("gold_medications", countMedications());
        stats.put("gold_labs", countLabs());
        MaterializerWatermarkEntity wm = getWatermark();
        if (wm != null) {
            stats.put("last_receipt_id", wm.getLastReceiptId());
            stats.put("last_stored_at", wm.getLastStoredAt());
            stats.put("processed_count", wm.getProcessedCount());
            stats.put("watermark_updated_at", wm.getUpdatedAt());
        }
        return stats;
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        return node.get(field).asText();
    }

    private String textOrDefault(JsonNode node, String field, String defaultVal) {
        String val = textOrNull(node, field);
        return val != null ? val : defaultVal;
    }

    private OffsetDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
