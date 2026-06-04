package zw.gov.mohcc.impilo.dispatch.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.dispatch.domain.*;
import zw.gov.mohcc.impilo.dispatch.repository.*;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class DeliveryOperationsService {

    private static final String AGGREGATE_TYPE = "DeliveryRequest";
    private static final String SUBJECT_TYPE = "DeliveryRequest";
    private static final String PRODUCER = "dispatch-service";

    private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
            "REQUESTED", Set.of("SUBMITTED", "APPROVED", "CANCELLED"),
            "SUBMITTED", Set.of("APPROVED", "REJECTED", "CANCELLED"),
            "APPROVED", Set.of("ASSIGNED", "CANCELLED"),
            "ASSIGNED", Set.of("ACCEPTED", "DECLINED", "PICKED_UP", "CANCELLED"),
            "ACCEPTED", Set.of("PICKED_UP", "DECLINED"),
            "PICKED_UP", Set.of("IN_TRANSIT", "FAILED", "RETURNED"),
            "IN_TRANSIT", Set.of("DELIVERED", "FAILED", "RETURNED")
    );

    private final DeliveryRequestRepository deliveryRepository;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryTrackingEventRepository trackingRepository;
    private final DeliveryProofRepository proofRepository;
    private final ChainOfCustodyEventRepository custodyRepository;
    private final FleetAssetRepository fleetRepository;
    private final DriverCourierProfileRepository courierRepository;
    private final DispatchZoneRepository zoneRepository;
    private final DeliveryPolicyRepository policyRepository;
    private final DeliveryIntegrationProviderRepository integrationRepository;
    private final AutonomousMissionRepository missionRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DeliveryOperationsService(
            DeliveryRequestRepository deliveryRepository,
            DeliveryAssignmentRepository assignmentRepository,
            DeliveryTrackingEventRepository trackingRepository,
            DeliveryProofRepository proofRepository,
            ChainOfCustodyEventRepository custodyRepository,
            FleetAssetRepository fleetRepository,
            DriverCourierProfileRepository courierRepository,
            DispatchZoneRepository zoneRepository,
            DeliveryPolicyRepository policyRepository,
            DeliveryIntegrationProviderRepository integrationRepository,
            AutonomousMissionRepository missionRepository,
            OutboxEventRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.deliveryRepository = deliveryRepository;
        this.assignmentRepository = assignmentRepository;
        this.trackingRepository = trackingRepository;
        this.proofRepository = proofRepository;
        this.custodyRepository = custodyRepository;
        this.fleetRepository = fleetRepository;
        this.courierRepository = courierRepository;
        this.zoneRepository = zoneRepository;
        this.policyRepository = policyRepository;
        this.integrationRepository = integrationRepository;
        this.missionRepository = missionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DeliveryRequestEntity createDelivery(UUID tenantId, String podId, String correlationId, String idempotencyKey, Map<String, Object> body) {
        DeliveryRequestEntity delivery = new DeliveryRequestEntity(
                UUID.randomUUID(),
                tenantId,
                str(body, "pickupLocation", "UNKNOWN_PICKUP"),
                str(body, "dropoffLocation", "UNKNOWN_DROPOFF"));
        delivery.setExternalRef(str(body, "externalRef", null));
        delivery.setRequesterActorId(str(body, "requesterActorId", null));
        delivery.setRequesterActorType(str(body, "requesterActorType", null));
        delivery.setPriority(str(body, "priority", "NORMAL"));
        delivery.setPayloadJson(toJson(body.get("payload")));
        delivery.setZoneId(toUuid(body.get("zoneId")));
        delivery.setPolicyId(toUuid(body.get("policyId")));
        delivery.setIntegrationProviderId(toUuid(body.get("integrationProviderId")));
        deliveryRepository.save(delivery);

        trackingRepository.save(new DeliveryTrackingEventEntity(delivery.getId(), "delivery.created"));
        custodyRepository.save(new ChainOfCustodyEventEntity(delivery.getId(), "delivery.requested"));
        publishDeliveryEvent(delivery, "dispatch.delivery.created", tenantId, podId, correlationId, idempotencyKey, buildDeliveryState(delivery));
        return delivery;
    }

    @Transactional(readOnly = true)
    public List<DeliveryRequestEntity> listDeliveries(UUID tenantId) {
        return deliveryRepository.findTop100ByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional(readOnly = true)
    public DeliveryRequestEntity getDelivery(UUID deliveryId) {
        return deliveryRepository.findById(deliveryId).orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
    }

    @Transactional
    public DeliveryRequestEntity patchDelivery(UUID deliveryId, Map<String, Object> body) {
        DeliveryRequestEntity delivery = getDelivery(deliveryId);
        if (body.containsKey("priority")) delivery.setPriority(str(body, "priority", delivery.getPriority()));
        if (body.containsKey("pickupLocation")) delivery.setPickupLocation(str(body, "pickupLocation", delivery.getPickupLocation()));
        if (body.containsKey("dropoffLocation")) delivery.setDropoffLocation(str(body, "dropoffLocation", delivery.getDropoffLocation()));
        delivery.setUpdatedAt(OffsetDateTime.now());
        return deliveryRepository.save(delivery);
    }

    @Transactional
    public DeliveryRequestEntity performAction(UUID deliveryId, UUID tenantId, String podId, String correlationId, String idempotencyKey,
                                               String action, Map<String, Object> body) {
        DeliveryRequestEntity delivery = getDelivery(deliveryId);
        switch (action) {
            case "submit" -> transition(delivery, "SUBMITTED", action, tenantId, podId, correlationId, idempotencyKey, body);
            case "approve" -> transition(delivery, "APPROVED", action, tenantId, podId, correlationId, idempotencyKey, body);
            case "reject" -> transition(delivery, "REJECTED", action, tenantId, podId, correlationId, idempotencyKey, body);
            case "cancel" -> transition(delivery, "CANCELLED", action, tenantId, podId, correlationId, idempotencyKey, body);
            case "assign" -> {
                createAssignment(delivery, body);
                transition(delivery, "ASSIGNED", action, tenantId, podId, correlationId, idempotencyKey, body);
            }
            case "reassign" -> {
                createAssignment(delivery, body);
                transition(delivery, "ASSIGNED", action, tenantId, podId, correlationId, idempotencyKey, body);
            }
            case "accept" -> {
                markLatestAssignment(delivery.getId(), "ACCEPTED");
                transition(delivery, "ACCEPTED", action, tenantId, podId, correlationId, idempotencyKey, body);
            }
            case "decline" -> {
                markLatestAssignment(delivery.getId(), "DECLINED");
                transition(delivery, "DECLINED", action, tenantId, podId, correlationId, idempotencyKey, body);
            }
            case "pickup" -> transition(delivery, "PICKED_UP", action, tenantId, podId, correlationId, idempotencyKey, body);
            case "start" -> transition(delivery, "IN_TRANSIT", action, tenantId, podId, correlationId, idempotencyKey, body);
            case "fail" -> transition(delivery, "FAILED", action, tenantId, podId, correlationId, idempotencyKey, body);
            case "return" -> transition(delivery, "RETURNED", action, tenantId, podId, correlationId, idempotencyKey, body);
            case "location" -> ingestLocation(delivery, tenantId, podId, correlationId, idempotencyKey, body);
            case "proof" -> captureProof(delivery, tenantId, podId, correlationId, idempotencyKey, body);
            default -> throw new IllegalArgumentException("Unsupported action: " + action);
        }
        return deliveryRepository.findById(deliveryId).orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTracking(UUID deliveryId) {
        List<DeliveryTrackingEventEntity> tracking = trackingRepository.findByDeliveryIdOrderByCreatedAtAsc(deliveryId);
        List<DeliveryProofEntity> proofs = proofRepository.findByDeliveryIdOrderByCapturedAtDesc(deliveryId);
        List<ChainOfCustodyEventEntity> custody = custodyRepository.findByDeliveryIdOrderByCreatedAtAsc(deliveryId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tracking", tracking.stream().map(this::toTrackingView).toList());
        out.put("proofs", proofs.stream().map(this::toProofView).toList());
        out.put("custody_events", custody.stream().map(this::toCustodyView).toList());
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(UUID tenantId) {
        List<DeliveryRequestEntity> deliveries = listDeliveries(tenantId);
        long inTransit = deliveries.stream().filter(d -> "IN_TRANSIT".equals(d.getStatus())).count();
        long delivered = deliveries.stream().filter(d -> "DELIVERED".equals(d.getStatus())).count();
        long failed = deliveries.stream().filter(d -> "FAILED".equals(d.getStatus())).count();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total_deliveries", deliveries.size());
        out.put("in_transit", inTransit);
        out.put("delivered", delivered);
        out.put("failed_or_returned", failed + deliveries.stream().filter(d -> "RETURNED".equals(d.getStatus())).count());
        out.put("active_fleet_assets", fleetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().filter(a -> "ACTIVE".equalsIgnoreCase(a.getStatus()) || "AVAILABLE".equalsIgnoreCase(a.getStatus())).count());
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dispatcherConsole(UUID tenantId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deliveries", listDeliveries(tenantId).stream().limit(20).map(this::toDeliveryView).toList());
        out.put("fleet", fleetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().limit(20).map(this::toFleetView).toList());
        out.put("couriers", courierRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().limit(20).map(this::toCourierView).toList());
        out.put("missions", missionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().limit(20).map(this::toMissionView).toList());
        return out;
    }

    @Transactional(readOnly = true)
    public List<FleetAssetEntity> listFleet(UUID tenantId) { return fleetRepository.findByTenantIdOrderByCreatedAtDesc(tenantId); }
    @Transactional(readOnly = true)
    public FleetAssetEntity getFleet(UUID id) { return fleetRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Fleet asset not found: " + id)); }
    @Transactional(readOnly = true)
    public List<DriverCourierProfileEntity> listCouriers(UUID tenantId) { return courierRepository.findByTenantIdOrderByCreatedAtDesc(tenantId); }
    @Transactional(readOnly = true)
    public DriverCourierProfileEntity getCourier(UUID id) { return courierRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Courier not found: " + id)); }
    @Transactional(readOnly = true)
    public List<DispatchZoneEntity> listZones(UUID tenantId) { return zoneRepository.findByTenantIdOrderByCreatedAtDesc(tenantId); }
    @Transactional(readOnly = true)
    public List<DeliveryPolicyEntity> listPolicies(UUID tenantId) { return policyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId); }
    @Transactional(readOnly = true)
    public List<DeliveryIntegrationProviderEntity> listIntegrations(UUID tenantId) { return integrationRepository.findByTenantIdOrderByCreatedAtDesc(tenantId); }
    @Transactional(readOnly = true)
    public List<AutonomousMissionEntity> listMissions(UUID tenantId) { return missionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId); }

    @Transactional
    public FleetAssetEntity createFleet(UUID tenantId, Map<String, Object> body) {
        FleetAssetEntity asset = new FleetAssetEntity(UUID.randomUUID(), tenantId, str(body, "assetCode", "ASSET-" + System.nanoTime()), str(body, "assetType", "VAN"));
        asset.setPlateNumber(str(body, "plateNumber", null));
        asset.setStatus(str(body, "status", "AVAILABLE"));
        asset.setCapacityKg(toBigDecimal(body.get("capacityKg")));
        return fleetRepository.save(asset);
    }

    @Transactional
    public FleetAssetEntity patchFleet(UUID id, Map<String, Object> body) {
        FleetAssetEntity asset = fleetRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Fleet asset not found: " + id));
        if (body.containsKey("status")) asset.setStatus(str(body, "status", asset.getStatus()));
        if (body.containsKey("plateNumber")) asset.setPlateNumber(str(body, "plateNumber", asset.getPlateNumber()));
        if (body.containsKey("capacityKg")) asset.setCapacityKg(toBigDecimal(body.get("capacityKg")));
        asset.setUpdatedAt(OffsetDateTime.now());
        return fleetRepository.save(asset);
    }

    @Transactional
    public DriverCourierProfileEntity createCourier(UUID tenantId, Map<String, Object> body) {
        DriverCourierProfileEntity courier = new DriverCourierProfileEntity(
                UUID.randomUUID(),
                tenantId,
                str(body, "courierCode", "COURIER-" + System.nanoTime()),
                str(body, "fullName", "Unknown Courier"));
        courier.setPhone(str(body, "phone", null));
        courier.setStatus(str(body, "status", "ACTIVE"));
        courier.setRating(toBigDecimal(body.get("rating")));
        return courierRepository.save(courier);
    }

    @Transactional
    public DriverCourierProfileEntity patchCourier(UUID id, Map<String, Object> body) {
        DriverCourierProfileEntity courier = courierRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Courier not found: " + id));
        if (body.containsKey("phone")) courier.setPhone(str(body, "phone", courier.getPhone()));
        if (body.containsKey("status")) courier.setStatus(str(body, "status", courier.getStatus()));
        if (body.containsKey("rating")) courier.setRating(toBigDecimal(body.get("rating")));
        courier.setUpdatedAt(OffsetDateTime.now());
        return courierRepository.save(courier);
    }

    @Transactional
    public DispatchZoneEntity createZone(UUID tenantId, Map<String, Object> body) {
        DispatchZoneEntity zone = new DispatchZoneEntity(
                UUID.randomUUID(),
                tenantId,
                str(body, "zoneCode", "ZONE-" + System.nanoTime()),
                str(body, "zoneName", "Default Zone"));
        zone.setBoundaryJson(toJson(body.get("boundary")));
        zone.setActive(Boolean.parseBoolean(str(body, "active", "true")));
        return zoneRepository.save(zone);
    }

    @Transactional
    public DeliveryPolicyEntity createPolicy(UUID tenantId, Map<String, Object> body) {
        DeliveryPolicyEntity policy = new DeliveryPolicyEntity(
                UUID.randomUUID(),
                tenantId,
                str(body, "policyCode", "POL-" + System.nanoTime()),
                str(body, "policyName", "Default Policy"));
        policy.setRulesJson(toJson(body.get("rules")));
        policy.setActive(Boolean.parseBoolean(str(body, "active", "true")));
        return policyRepository.save(policy);
    }

    @Transactional
    public DeliveryIntegrationProviderEntity createIntegration(UUID tenantId, Map<String, Object> body) {
        DeliveryIntegrationProviderEntity provider = new DeliveryIntegrationProviderEntity(
                UUID.randomUUID(),
                tenantId,
                str(body, "providerCode", "provider-" + System.nanoTime()),
                str(body, "providerName", "Provider"));
        provider.setConfigJson(toJson(body.get("config")));
        provider.setStatus(str(body, "status", "ACTIVE"));
        return integrationRepository.save(provider);
    }

    @Transactional
    public AutonomousMissionEntity createMission(UUID tenantId, String podId, String correlationId, String idempotencyKey, Map<String, Object> body) {
        AutonomousMissionEntity mission = new AutonomousMissionEntity(
                UUID.randomUUID(),
                tenantId,
                str(body, "missionCode", "MSN-" + System.nanoTime()));
        mission.setDeliveryId(toUuid(body.get("deliveryId")));
        mission.setVehicleRef(str(body, "vehicleRef", null));
        mission.setStatus(str(body, "status", "PLANNED"));
        mission.setMissionPayloadJson(toJson(body.get("payload")));
        missionRepository.save(mission);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mission_id", mission.getId().toString());
        payload.put("mission_code", mission.getMissionCode());
        payload.put("status", mission.getStatus());
        appendOutbox("dispatch.autonomous.mission.created", mission.getId().toString(), tenantId, podId, correlationId, idempotencyKey, payload);
        return mission;
    }

    @Transactional
    public Map<String, Object> handleWebhook(UUID tenantId, String providerCode, String podId, String correlationId, String idempotencyKey, Map<String, Object> payload) {
        DeliveryIntegrationProviderEntity provider = integrationRepository.findByTenantIdAndProviderCode(tenantId, providerCode)
                .orElseThrow(() -> new IllegalArgumentException("Integration provider not found: " + providerCode));
        provider.setLastWebhookAt(OffsetDateTime.now());
        provider.setUpdatedAt(OffsetDateTime.now());
        integrationRepository.save(provider);
        appendOutbox("dispatch.delivery.webhook.received", provider.getId().toString(), tenantId, podId, correlationId, idempotencyKey, Map.of("provider_code", providerCode, "payload", payload));
        return Map.of("provider_code", providerCode, "accepted", true, "received_at", OffsetDateTime.now().toString());
    }

    private void createAssignment(DeliveryRequestEntity delivery, Map<String, Object> body) {
        DeliveryAssignmentEntity assignment = new DeliveryAssignmentEntity(UUID.randomUUID(), delivery.getId());
        assignment.setFleetAssetId(toUuid(body.get("fleetAssetId")));
        assignment.setCourierProfileId(toUuid(body.get("courierProfileId")));
        assignment.setNotesJson(toJson(body.get("notes")));
        assignmentRepository.save(assignment);
    }

    private void markLatestAssignment(UUID deliveryId, String status) {
        assignmentRepository.findFirstByDeliveryIdOrderByAssignedAtDesc(deliveryId).ifPresent(assignment -> {
            assignment.setAssignmentStatus(status);
            if ("ACCEPTED".equals(status)) assignment.setAcceptedAt(OffsetDateTime.now());
            assignment.setUpdatedAt(OffsetDateTime.now());
            assignmentRepository.save(assignment);
        });
    }

    private void ingestLocation(DeliveryRequestEntity delivery, UUID tenantId, String podId, String correlationId, String idempotencyKey, Map<String, Object> body) {
        DeliveryTrackingEventEntity event = new DeliveryTrackingEventEntity(delivery.getId(), "delivery.location");
        event.setLatitude(toDouble(body.get("latitude")));
        event.setLongitude(toDouble(body.get("longitude")));
        event.setAccuracyMeters(toDouble(body.get("accuracyMeters")));
        event.setSource(str(body, "source", "mobile"));
        event.setDetailsJson(toJson(body));
        trackingRepository.save(event);
        publishDeliveryEvent(delivery, "dispatch.delivery.location.ingested", tenantId, podId, correlationId, idempotencyKey, Map.of("delivery_id", delivery.getId(), "location", body));
    }

    private void captureProof(DeliveryRequestEntity delivery, UUID tenantId, String podId, String correlationId, String idempotencyKey, Map<String, Object> body) {
        DeliveryProofEntity proof = new DeliveryProofEntity(UUID.randomUUID(), delivery.getId(), str(body, "proofType", "SIGNATURE"));
        proof.setProofRef(str(body, "proofRef", null));
        proof.setOtpCode(str(body, "otp", null));
        proof.setCapturedBy(str(body, "capturedBy", null));
        proof.setDetailsJson(toJson(body));
        proofRepository.save(proof);

        custodyRepository.save(new ChainOfCustodyEventEntity(delivery.getId(), "proof.captured"));
        if ("IN_TRANSIT".equals(delivery.getStatus()) || "PICKED_UP".equals(delivery.getStatus())) {
            transition(delivery, "DELIVERED", "proof", tenantId, podId, correlationId, idempotencyKey, body);
        } else {
            publishDeliveryEvent(delivery, "dispatch.delivery.proof.captured", tenantId, podId, correlationId, idempotencyKey, Map.of("delivery_id", delivery.getId(), "proof_id", proof.getId()));
        }
    }

    private void transition(DeliveryRequestEntity delivery, String target, String action,
                            UUID tenantId, String podId, String correlationId, String idempotencyKey,
                            Map<String, Object> details) {
        validateTransition(delivery.getStatus(), target);
        String previous = delivery.getStatus();
        delivery.setStatus(target);
        delivery.setUpdatedAt(OffsetDateTime.now());
        deliveryRepository.save(delivery);

        DeliveryTrackingEventEntity tracking = new DeliveryTrackingEventEntity(delivery.getId(), "delivery." + action);
        tracking.setDetailsJson(toJson(details));
        trackingRepository.save(tracking);

        ChainOfCustodyEventEntity custody = new ChainOfCustodyEventEntity(delivery.getId(), "delivery." + action);
        custody.setActorId(str(details, "actorId", null));
        custody.setActorType(str(details, "actorType", null));
        custody.setNotesJson(toJson(details.get("notes")));
        custodyRepository.save(custody);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("delivery_id", delivery.getId().toString());
        payload.put("previous_status", previous);
        payload.put("new_status", target);
        payload.put("action", action);
        payload.put("details", details);
        publishDeliveryEvent(delivery, "dispatch.delivery." + action, tenantId, podId, correlationId, idempotencyKey, payload);
    }

    private void validateTransition(String from, String to) {
        if (from.equals(to)) return;
        Set<String> allowed = STATUS_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new InvalidStatusTransitionException(from, to);
        }
    }

    private void publishDeliveryEvent(DeliveryRequestEntity delivery, String eventType, UUID tenantId, String podId, String correlationId, String idempotencyKey, Map<String, Object> payload) {
        appendOutbox(eventType, delivery.getId().toString(), tenantId, podId, correlationId, idempotencyKey, payload);
    }

    private void appendOutbox(String eventType, String aggregateId, UUID tenantId, String podId, String correlationId, String idempotencyKey, Map<String, Object> payload) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType(AGGREGATE_TYPE);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setSchemaVersion("1");
        outbox.setCorrelationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setCausationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setProducer(PRODUCER);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(aggregateId);
        outbox.setSubjectType(SUBJECT_TYPE);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(aggregateId);
        outboxRepository.save(outbox);
    }

    public Map<String, Object> toDeliveryView(DeliveryRequestEntity d) {
        return buildDeliveryState(d);
    }

    public Map<String, Object> toFleetView(FleetAssetEntity f) {
        return Map.of(
                "id", f.getId().toString(),
                "asset_code", f.getAssetCode(),
                "asset_type", f.getAssetType(),
                "status", f.getStatus(),
                "plate_number", Optional.ofNullable(f.getPlateNumber()).orElse(""));
    }

    public Map<String, Object> toCourierView(DriverCourierProfileEntity c) {
        return Map.of(
                "id", c.getId().toString(),
                "courier_code", c.getCourierCode(),
                "full_name", c.getFullName(),
                "status", c.getStatus(),
                "phone", Optional.ofNullable(c.getPhone()).orElse(""));
    }

    public Map<String, Object> toMissionView(AutonomousMissionEntity m) {
        return Map.of(
                "id", m.getId().toString(),
                "mission_code", m.getMissionCode(),
                "status", m.getStatus(),
                "delivery_id", Optional.ofNullable(m.getDeliveryId()).map(UUID::toString).orElse(""));
    }

    public Map<String, Object> toTrackingView(DeliveryTrackingEventEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("event_type", e.getEventType());
        m.put("latitude", e.getLatitude());
        m.put("longitude", e.getLongitude());
        m.put("accuracy_meters", e.getAccuracyMeters());
        m.put("source", e.getSource());
        m.put("created_at", e.getCreatedAt());
        return m;
    }

    public Map<String, Object> toProofView(DeliveryProofEntity p) {
        return Map.of(
                "id", p.getId().toString(),
                "proof_type", p.getProofType(),
                "proof_ref", Optional.ofNullable(p.getProofRef()).orElse(""),
                "otp", Optional.ofNullable(p.getOtpCode()).orElse(""),
                "captured_at", p.getCapturedAt().toString());
    }

    public Map<String, Object> toCustodyView(ChainOfCustodyEventEntity e) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", e.getId());
        out.put("event_type", e.getEventType());
        out.put("actor_id", e.getActorId());
        out.put("actor_type", e.getActorType());
        out.put("created_at", e.getCreatedAt());
        return out;
    }

    private Map<String, Object> buildDeliveryState(DeliveryRequestEntity d) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", d.getId().toString());
        out.put("tenant_id", d.getTenantId().toString());
        out.put("external_ref", d.getExternalRef());
        out.put("status", d.getStatus());
        out.put("priority", d.getPriority());
        out.put("pickup_location", d.getPickupLocation());
        out.put("dropoff_location", d.getDropoffLocation());
        out.put("requester_actor_id", d.getRequesterActorId());
        out.put("requester_actor_type", d.getRequesterActorType());
        out.put("created_at", d.getCreatedAt());
        out.put("updated_at", d.getUpdatedAt());
        return out;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    private String str(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value != null ? value.toString() : fallback;
    }

    private UUID toUuid(Object value) {
        if (value == null) return null;
        try { return UUID.fromString(value.toString()); } catch (Exception ex) { return null; }
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        try { return Double.parseDouble(value.toString()); } catch (NumberFormatException ex) { return null; }
    }

    private java.math.BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        try { return new java.math.BigDecimal(value.toString()); } catch (NumberFormatException ex) { return null; }
    }

    public static class DeliveryNotFoundException extends RuntimeException {
        public DeliveryNotFoundException(UUID id) { super("Delivery not found: " + id); }
    }
}
