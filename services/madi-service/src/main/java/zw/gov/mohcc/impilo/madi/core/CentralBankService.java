package zw.gov.mohcc.impilo.madi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.integration.NhumeIntegration;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodEmergencyRedistributionEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.BloodEmergencyRedistributionRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CentralBankService {

    private final BloodEmergencyRedistributionRepository redistributionRepository;
    private final MadiEventEmitter eventEmitter;
    private final NhumeIntegration nhumeIntegration;

    public CentralBankService(BloodEmergencyRedistributionRepository redistributionRepository,
                              MadiEventEmitter eventEmitter,
                              NhumeIntegration nhumeIntegration) {
        this.redistributionRepository = redistributionRepository;
        this.eventEmitter = eventEmitter;
        this.nhumeIntegration = nhumeIntegration;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listEmergencyRedistributions(UUID tenantId) {
        return redistributionRepository.findByTenantIdOrderByRequestedAtDesc(tenantId).stream()
                .map(this::toMap)
                .toList();
    }

    @Transactional
    public Map<String, Object> requestEmergencyRedistribution(UUID tenantId, Map<String, Object> body) {
        UUID sourceFacilityId = parseUuid(body.get("source_facility_id"), "source_facility_id");
        UUID targetFacilityId = parseUuid(body.get("target_facility_id"), "target_facility_id");
        String bloodGroup = requireString(body.get("blood_group"), "blood_group");
        String componentType = requireString(body.get("component_type"), "component_type");
        int unitsRequested = parsePositiveInt(body.get("units_requested"), "units_requested");

        BloodEmergencyRedistributionEntity entity = new BloodEmergencyRedistributionEntity();
        entity.setTenantId(tenantId);
        entity.setSourceFacilityId(sourceFacilityId);
        entity.setTargetFacilityId(targetFacilityId);
        entity.setBloodGroup(bloodGroup);
        entity.setComponentType(componentType);
        entity.setUnitsRequested(unitsRequested);
        entity.setReason(stringOrNull(body.get("reason")));
        entity.setRequestedBy(stringOrNull(body.get("requested_by")));
        entity.setFacilityId(parseUuidOrNull(body.get("facility_id")));
        entity.setPriority(stringOrDefault(body.get("priority"), "EMERGENCY"));
        entity.setStatus("REQUESTED");

        BloodEmergencyRedistributionEntity saved = redistributionRepository.save(entity);
        eventEmitter.emit("CENTRAL_BANK", saved.getRedistributionId().toString(),
                "EMERGENCY_REDISTRIBUTION_REQUESTED", "BLOOD_INVENTORY",
                saved.getRedistributionId().toString(),
                Map.of("bloodGroup", bloodGroup, "componentType", componentType,
                        "unitsRequested", unitsRequested, "targetFacilityId", targetFacilityId.toString()),
                tenantId);
        return toMap(saved);
    }

    @Transactional
    public Map<String, Object> approveEmergencyRedistribution(UUID tenantId, UUID redistributionId,
                                                             Map<String, Object> body) {
        BloodEmergencyRedistributionEntity entity = redistributionRepository
                .findByRedistributionIdAndTenantId(redistributionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Emergency redistribution not found"));

        if ("FULFILLED".equals(entity.getStatus()) || "CANCELLED".equals(entity.getStatus())) {
            throw new IllegalStateException("Redistribution already closed");
        }

        int unitsAllocated = body.containsKey("units_allocated")
                ? parsePositiveInt(body.get("units_allocated"), "units_allocated")
                : entity.getUnitsRequested();
        String approvedBy = requireString(body.get("approved_by"), "approved_by");
        String nextStatus = stringOrDefault(body.get("status"), "APPROVED");

        entity.setUnitsAllocated(unitsAllocated);
        entity.setApprovedBy(approvedBy);
        entity.setApprovedAt(OffsetDateTime.now());
        entity.setStatus(nextStatus);
        if ("FULFILLED".equals(nextStatus)) {
            entity.setFulfilledAt(OffsetDateTime.now());
        }

        BloodEmergencyRedistributionEntity saved = redistributionRepository.save(entity);
        if ("APPROVED".equals(nextStatus) || "FULFILLED".equals(nextStatus)) {
            saved = initiatePhysicalHandoff(saved, approvedBy);
        }
        eventEmitter.emit("CENTRAL_BANK", saved.getRedistributionId().toString(),
                "EMERGENCY_REDISTRIBUTION_" + nextStatus, "BLOOD_INVENTORY",
                saved.getRedistributionId().toString(),
                Map.of("unitsAllocated", unitsAllocated, "approvedBy", approvedBy),
                tenantId);
        return toMap(saved);
    }

    @Transactional
    public Map<String, Object> initiatePhysicalHandoff(UUID tenantId, UUID redistributionId, String actorId) {
        BloodEmergencyRedistributionEntity entity = redistributionRepository
                .findByRedistributionIdAndTenantId(redistributionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Emergency redistribution not found"));
        BloodEmergencyRedistributionEntity saved = initiatePhysicalHandoff(entity, actorId);
        return toMap(saved);
    }

    private BloodEmergencyRedistributionEntity initiatePhysicalHandoff(
            BloodEmergencyRedistributionEntity entity, String actorId) {
        if (entity.getNhumeDeliveryId() != null) {
            return entity;
        }
        Optional<UUID> deliveryId = nhumeIntegration.createBloodRedistributionDelivery(
                entity.getRedistributionId(),
                entity.getSourceFacilityId(),
                entity.getTargetFacilityId(),
                entity.getBloodGroup(),
                entity.getComponentType(),
                entity.getUnitsAllocated() != null && entity.getUnitsAllocated() > 0
                        ? entity.getUnitsAllocated()
                        : entity.getUnitsRequested(),
                actorId != null ? actorId : entity.getApprovedBy(),
                entity.getReason());
        if (deliveryId.isPresent()) {
            entity.setNhumeDeliveryId(deliveryId.get());
            entity.setHandoffStatus("DISPATCH_REQUESTED");
            entity.setHandoffAt(OffsetDateTime.now());
            entity.setHandoffError(null);
            eventEmitter.emit("CENTRAL_BANK", entity.getRedistributionId().toString(),
                    "EMERGENCY_REDISTRIBUTION_HANDOFF", "NHUME_DELIVERY",
                    deliveryId.get().toString(),
                    Map.of("nhumeDeliveryId", deliveryId.get().toString()),
                    entity.getTenantId());
        } else {
            entity.setHandoffStatus("HANDOFF_FAILED");
            entity.setHandoffError("NHUME delivery creation unavailable — retry handoff when logistics service is reachable.");
        }
        return redistributionRepository.save(entity);
    }

    private Map<String, Object> toMap(BloodEmergencyRedistributionEntity entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("redistributionId", entity.getRedistributionId());
        row.put("sourceFacilityId", entity.getSourceFacilityId());
        row.put("targetFacilityId", entity.getTargetFacilityId());
        row.put("bloodGroup", entity.getBloodGroup());
        row.put("componentType", entity.getComponentType());
        row.put("unitsRequested", entity.getUnitsRequested());
        row.put("unitsAllocated", entity.getUnitsAllocated());
        row.put("priority", entity.getPriority());
        row.put("status", entity.getStatus());
        row.put("reason", entity.getReason());
        row.put("requestedBy", entity.getRequestedBy());
        row.put("approvedBy", entity.getApprovedBy());
        row.put("requestedAt", entity.getRequestedAt());
        row.put("approvedAt", entity.getApprovedAt());
        row.put("fulfilledAt", entity.getFulfilledAt());
        row.put("nhumeDeliveryId", entity.getNhumeDeliveryId());
        row.put("handoffStatus", entity.getHandoffStatus());
        row.put("handoffAt", entity.getHandoffAt());
        row.put("handoffError", entity.getHandoffError());
        if (entity.getNhumeDeliveryId() != null) {
            row.put("nhumeDeepLinkPath", "/nhume/deliveries/" + entity.getNhumeDeliveryId());
        }
        return row;
    }

    private static UUID parseUuid(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(field + " must be a valid UUID");
        }
    }

    private static UUID parseUuidOrNull(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return parseUuid(value, "facility_id");
    }

    private static String requireString(Object value, String field) {
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.toString().trim();
    }

    private static String stringOrNull(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private static String stringOrDefault(Object value, String fallback) {
        String s = stringOrNull(value);
        return s != null ? s : fallback;
    }

    private static int parsePositiveInt(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        int parsed;
        if (value instanceof Number number) {
            parsed = number.intValue();
        } else {
            parsed = Integer.parseInt(value.toString());
        }
        if (parsed <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return parsed;
    }
}
