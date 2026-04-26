package zw.gov.mohcc.impilo.costa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.costa.domain.entity.CostEventEntity;
import zw.gov.mohcc.impilo.costa.domain.repository.CostEventRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CostEventCaptureService {

    private static final Logger log = LoggerFactory.getLogger(CostEventCaptureService.class);

    private final CostEventRepository costEventRepository;
    private final ObjectMapper objectMapper;

    public CostEventCaptureService(CostEventRepository costEventRepository, ObjectMapper objectMapper) {
        this.costEventRepository = costEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<CostEventEntity> listForEncounter(UUID tenantId, String encounterId) {
        return costEventRepository.findTop200ByTenantIdAndEncounterIdOrderByCreatedAtDesc(tenantId, encounterId);
    }

    @Transactional(readOnly = true)
    public List<CostEventEntity> listForPatient(UUID tenantId, String patientCpid) {
        return costEventRepository.findTop200ByTenantIdAndPatientCpidOrderByCreatedAtDesc(tenantId, patientCpid);
    }

    @Transactional
    public CostEventEntity captureFromTrustApi(JsonNode body) {
        var ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();
        return persistFromBody(body, tenantId, ctx.actorId());
    }

    @Transactional
    public CostEventEntity captureFromSignal(JsonNode body) {
        if (!body.hasNonNull("tenant_id")) {
            throw new IllegalArgumentException("tenant_id is required on cost signals");
        }
        UUID tenantId = UUID.fromString(body.get("tenant_id").asText());
        String actor = body.path("created_by").asText(null);
        return persistFromBody(body, tenantId, actor);
    }

    private CostEventEntity persistFromBody(JsonNode body, UUID tenantId, String createdBy) {
        CostEventEntity e = new CostEventEntity();
        e.setCostEventId(UlidGenerator.generate());
        e.setTenantId(tenantId);
        if (body.hasNonNull("patient_cpid")) {
            e.setPatientCpid(body.get("patient_cpid").asText());
        }
        if (body.hasNonNull("encounter_id")) {
            e.setEncounterId(body.get("encounter_id").asText());
        }
        if (body.hasNonNull("facility_id")) {
            e.setFacilityId(UUID.fromString(body.get("facility_id").asText()));
        }
        if (body.hasNonNull("workspace_id")) {
            e.setWorkspaceId(UUID.fromString(body.get("workspace_id").asText()));
        }
        if (body.hasNonNull("provider_id")) {
            e.setProviderId(UUID.fromString(body.get("provider_id").asText()));
        }
        if (body.hasNonNull("provider_role")) {
            e.setProviderRole(body.get("provider_role").asText());
        }
        e.setEventType(body.path("event_type").asText("UNSPECIFIED"));
        e.setEventSource(body.path("event_source").asText("API"));
        if (body.hasNonNull("service_domain")) {
            e.setServiceDomain(body.get("service_domain").asText());
        }
        if (body.hasNonNull("service_category")) {
            e.setServiceCategory(body.get("service_category").asText());
        }
        if (body.hasNonNull("start_time")) {
            e.setStartTime(OffsetDateTime.parse(body.get("start_time").asText()));
        }
        if (body.hasNonNull("end_time")) {
            e.setEndTime(OffsetDateTime.parse(body.get("end_time").asText()));
        }
        if (body.has("duration_minutes") && body.get("duration_minutes").canConvertToInt()) {
            e.setDurationMinutes(body.get("duration_minutes").asInt());
        }
        if (body.hasNonNull("quantity")) {
            e.setQuantity(new BigDecimal(body.get("quantity").asText()));
        }
        if (body.hasNonNull("unit_of_measure")) {
            e.setUnitOfMeasure(body.get("unit_of_measure").asText());
        }
        if (body.hasNonNull("linked_order_id")) {
            e.setLinkedOrderId(body.get("linked_order_id").asText());
        }
        if (body.hasNonNull("linked_procedure_id")) {
            e.setLinkedProcedureId(body.get("linked_procedure_id").asText());
        }
        if (body.hasNonNull("linked_charge_sheet_id")) {
            e.setLinkedChargeSheetId(body.get("linked_charge_sheet_id").asText());
        }
        e.setCostingStatus(body.path("costing_status").asText("captured"));
        try {
            if (body.has("metadata") && body.get("metadata").isObject()) {
                e.setMetadata(objectMapper.writeValueAsString(objectMapper.convertValue(body.get("metadata"), Map.class)));
            } else {
                e.setMetadata("{}");
            }
        } catch (Exception ex) {
            e.setMetadata("{}");
        }
        e.setCreatedBy(createdBy);
        return costEventRepository.save(e);
    }

    /**
     * Best-effort capture from clinical integration events (never throws to callers).
     */
    public void tryCaptureClinical(String eventType, String eventSource, UUID tenantId, String patientCpid,
                                   String encounterId, UUID facilityId, ObjectNode metadata) {
        try {
            CostEventEntity e = new CostEventEntity();
            e.setCostEventId(UlidGenerator.generate());
            e.setTenantId(tenantId);
            e.setPatientCpid(patientCpid);
            e.setEncounterId(encounterId);
            e.setFacilityId(facilityId);
            e.setEventType(eventType);
            e.setEventSource(eventSource);
            e.setCostingStatus("captured");
            e.setMetadata(metadata != null ? metadata.toString() : "{}");
            costEventRepository.save(e);
        } catch (Exception ex) {
            log.warn("cost event capture skipped: {} {}", eventType, ex.getMessage());
        }
    }
}
