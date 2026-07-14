package zw.gov.mohcc.impilo.inventory.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.api.dto.ImplantTraceDto;
import zw.gov.mohcc.impilo.inventory.api.dto.RecordImplantRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.RecordImplantResponse;
import zw.gov.mohcc.impilo.inventory.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ImplantUnitEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.PatientImplantEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ImplantUnitRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.PatientImplantRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implant / device UDI + serial traceability.
 *
 * <p>Devices are tracked at the physical UNIT level (finer than a lot): a unit
 * is identified by its UDI and/or serial number. Recording an implant is
 * idempotent on the UDI — if a unit with that UDI already exists it is reused
 * and marked IMPLANTED, otherwise a new unit is registered. A patient/episode
 * link is then created. Recall traceability answers: given a recalled UDI or
 * lot, which patients received it?</p>
 */
@Service
public class ImplantTraceabilityService {

    private static final Logger log = LoggerFactory.getLogger(ImplantTraceabilityService.class);

    private final ImplantUnitRepository unitRepository;
    private final PatientImplantRepository patientImplantRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ImplantTraceabilityService(ImplantUnitRepository unitRepository,
                                      PatientImplantRepository patientImplantRepository,
                                      EventOutboxRepository outboxRepository,
                                      ObjectMapper objectMapper) {
        this.unitRepository = unitRepository;
        this.patientImplantRepository = patientImplantRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RecordImplantResponse recordImplant(RecordImplantRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenant = ctx.tenantId();

        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (request.patientCpid() == null || request.patientCpid().isBlank()) {
            throw new IllegalArgumentException("patientCpid is required");
        }
        boolean hasUdi = request.udi() != null && !request.udi().isBlank();

        // Look up or create the physical unit — idempotent on (tenant, udi).
        ImplantUnitEntity unit = null;
        if (hasUdi) {
            unit = unitRepository.findByTenantIdAndUdi(tenant, request.udi()).orElse(null);
        }
        if (unit == null) {
            unit = new ImplantUnitEntity();
            unit.setTenantId(tenant);
            unit.setUdi(hasUdi ? request.udi() : null);
            unit.setSerialNumber(request.serialNumber());
            unit.setLotNumber(request.lotNumber());
            unit.setExpiryDate(request.expiryDate());
            unit.setItemCode(request.itemCode());
            unit.setDeviceType(request.deviceType());
            unit.setBrand(request.brand());
            unit.setModel(request.model());
            unit.setBatchLotId(request.batchLotId());
            unit.setCreatedBy(ctx.actorId());
        }
        unit.setStatus("IMPLANTED");
        unit = unitRepository.save(unit);

        // Link the unit to the patient / episode.
        PatientImplantEntity link = new PatientImplantEntity();
        link.setTenantId(tenant);
        link.setImplantUnitId(unit.getImplantUnitId());
        link.setPatientCpid(request.patientCpid());
        link.setEpisodeRef(request.episodeRef());
        link.setBodySite(request.bodySite());
        link.setLaterality(request.laterality());
        link.setImplantedBy(request.implantedBy());
        link.setImplantedAt(request.implantedAt());
        link.setStatus("IMPLANTED");
        link = patientImplantRepository.save(link);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("implantUnitId", unit.getImplantUnitId());
        event.put("patientImplantId", link.getPatientImplantId());
        event.put("udi", unit.getUdi());
        event.put("serialNumber", unit.getSerialNumber());
        event.put("lotNumber", unit.getLotNumber());
        event.put("itemCode", unit.getItemCode());
        event.put("patientCpid", link.getPatientCpid());
        event.put("episodeRef", link.getEpisodeRef());
        event.put("bodySite", link.getBodySite());
        event.put("laterality", link.getLaterality());
        event.put("status", unit.getStatus());
        publishOutboxEvent("IMPLANT", link.getPatientImplantId().toString(),
                "inventory.implant.recorded", event, tenant);

        log.info("Implant recorded: unit={}, patientLink={}, udi={}, patient={}",
                unit.getImplantUnitId(), link.getPatientImplantId(), unit.getUdi(), link.getPatientCpid());

        return new RecordImplantResponse(unit.getImplantUnitId(), link.getPatientImplantId(),
                unit.getUdi(), unit.getSerialNumber(), unit.getLotNumber(), unit.getStatus());
    }

    /**
     * Recall traceability: given a recalled UDI and/or lot number, return every
     * patient/episode that received a matching unit. At least one of udi/lot
     * must be supplied.
     */
    @Transactional(readOnly = true)
    public List<ImplantTraceDto> traceByRecall(String udi, String lot) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenant = ctx.tenantId();

        boolean hasUdi = udi != null && !udi.isBlank();
        boolean hasLot = lot != null && !lot.isBlank();
        if (!hasUdi && !hasLot) {
            throw new IllegalArgumentException("Either udi or lot is required for recall trace");
        }

        // Collect matching physical units by udi or lot.
        Map<UUID, ImplantUnitEntity> unitsById = new LinkedHashMap<>();
        if (hasUdi) {
            unitRepository.findByTenantIdAndUdi(tenant, udi)
                    .ifPresent(u -> unitsById.put(u.getImplantUnitId(), u));
        }
        if (hasLot) {
            for (ImplantUnitEntity u : unitRepository.findByTenantIdAndLotNumber(tenant, lot)) {
                unitsById.put(u.getImplantUnitId(), u);
            }
        }
        if (unitsById.isEmpty()) {
            return Collections.emptyList();
        }

        List<PatientImplantEntity> links = patientImplantRepository
                .findByTenantIdAndImplantUnitIdInOrderByImplantedAtDesc(tenant, unitsById.keySet());
        return links.stream()
                .map(l -> ImplantTraceDto.from(l, unitsById.get(l.getImplantUnitId())))
                .collect(Collectors.toList());
    }

    /**
     * All implants recorded for a patient.
     */
    @Transactional(readOnly = true)
    public List<ImplantTraceDto> listByPatient(String patientCpid) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenant = ctx.tenantId();
        if (patientCpid == null || patientCpid.isBlank()) {
            throw new IllegalArgumentException("patientCpid is required");
        }
        List<PatientImplantEntity> links =
                patientImplantRepository.findByTenantIdAndPatientCpidOrderByImplantedAtDesc(tenant, patientCpid);
        List<ImplantTraceDto> result = new ArrayList<>();
        for (PatientImplantEntity l : links) {
            ImplantUnitEntity unit = unitRepository.findById(l.getImplantUnitId()).orElse(null);
            result.add(ImplantTraceDto.from(l, unit));
        }
        return result;
    }

    /**
     * Trace a single unit by UDI: the unit plus every patient it was implanted in.
     */
    @Transactional(readOnly = true)
    public List<ImplantTraceDto> traceByUdi(String udi) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenant = ctx.tenantId();
        if (udi == null || udi.isBlank()) {
            throw new IllegalArgumentException("udi is required");
        }
        Optional<ImplantUnitEntity> unitOpt = unitRepository.findByTenantIdAndUdi(tenant, udi);
        if (unitOpt.isEmpty()) {
            return Collections.emptyList();
        }
        ImplantUnitEntity unit = unitOpt.get();
        List<PatientImplantEntity> links = patientImplantRepository
                .findByTenantIdAndImplantUnitIdInOrderByImplantedAtDesc(
                        tenant, Collections.singletonList(unit.getImplantUnitId()));
        return links.stream()
                .map(l -> ImplantTraceDto.from(l, unit))
                .collect(Collectors.toList());
    }

    private void publishOutboxEvent(String aggregateType, String aggregateId,
                                    String eventType, Object payload, UUID tenantId) {
        try {
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType(aggregateType);
            outbox.setAggregateId(aggregateId);
            outbox.setEventType(eventType);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(tenantId);
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write outbox event: type={}, aggregateId={}", eventType, aggregateId, e);
        }
    }
}
