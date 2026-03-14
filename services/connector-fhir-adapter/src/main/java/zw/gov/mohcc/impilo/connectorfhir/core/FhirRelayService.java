package zw.gov.mohcc.impilo.connectorfhir.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.connectorfhir.api.dto.*;
import zw.gov.mohcc.impilo.connectorfhir.domain.*;
import zw.gov.mohcc.impilo.connectorfhir.repository.*;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class FhirRelayService {

    private static final Logger log = LoggerFactory.getLogger(FhirRelayService.class);

    private final RelayDestinationRepository destinationRepository;
    private final RelayAuditRepository auditRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final String defaultDestination;

    public FhirRelayService(RelayDestinationRepository destinationRepository,
                              RelayAuditRepository auditRepository,
                              OutboxEventRepository outboxRepository,
                              ObjectMapper objectMapper,
                              @Value("${impilo.fhir.default-destination}") String defaultDestination) {
        this.destinationRepository = destinationRepository;
        this.auditRepository = auditRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.defaultDestination = defaultDestination;
    }

    @Transactional
    public RelayAuditEntity relayBundle(UUID tenantId, String podId, String correlationId,
                                          String idempotencyKey, RelayBundleRequest request, String actorRef) {
        // Resolve destination
        RelayDestinationEntity destination = null;
        String destinationUrl = defaultDestination;
        UUID destinationId = null;

        if (request.destinationId() != null) {
            destination = destinationRepository.findById(request.destinationId())
                    .orElseThrow(() -> new DestinationNotFoundException("Destination not found: " + request.destinationId()));
            if (!destination.isEnabled()) {
                throw new DestinationNotFoundException("Destination is disabled: " + request.destinationId());
            }
            destinationUrl = destination.getEndpointUrl();
            destinationId = destination.getDestinationId();

            // Validate resource type against destination filter
            if (!"*".equals(destination.getResourceTypes())
                    && !destination.getResourceTypes().contains(request.resourceType())) {
                // Record REJECTED decision
                UUID auditId = UUID.randomUUID();
                String bundleId = "bundle-" + auditId;
                RelayAuditEntity audit = new RelayAuditEntity(auditId, tenantId, bundleId,
                        request.resourceType(), request.entries().size(), destinationId, destinationUrl,
                        "REJECTED", correlationId != null ? UUID.fromString(correlationId) : null, actorRef);
                audit.setOutcome("REJECTED");
                audit.setErrorMessage("Resource type not allowed for destination: " + request.resourceType());
                audit.setCompletedAt(OffsetDateTime.now());
                auditRepository.save(audit);

                appendOutboxEvent("impilo.integration.relay.rejected.v1", auditId.toString(),
                        tenantId, podId, correlationId, idempotencyKey,
                        Map.of("audit_id", auditId.toString(), "reason", "RESOURCE_TYPE_MISMATCH",
                                "resource_type", request.resourceType()),
                        tenantId.toString());

                log.info("Rejected relay [auditId={}, resourceType={}, destination={}]",
                        auditId, request.resourceType(), destinationUrl);
                return audit;
            }
        }

        // Create audit record with ACCEPTED decision
        UUID auditId = UUID.randomUUID();
        String bundleId = "bundle-" + auditId;
        RelayAuditEntity audit = new RelayAuditEntity(auditId, tenantId, bundleId,
                request.resourceType(), request.entries().size(), destinationId, destinationUrl,
                "ACCEPTED", correlationId != null ? UUID.fromString(correlationId) : null, actorRef);
        audit.setOutcome("RELAYED");
        audit.setCompletedAt(OffsetDateTime.now());
        auditRepository.save(audit);

        // Emit relay event
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("audit_id", auditId.toString());
        payload.put("bundle_id", bundleId);
        payload.put("resource_type", request.resourceType());
        payload.put("resource_count", request.entries().size());
        payload.put("destination_url", destinationUrl);
        payload.put("decision", "ACCEPTED");
        payload.put("outcome", "RELAYED");

        appendOutboxEvent("impilo.integration.relay.accepted.v1", auditId.toString(),
                tenantId, podId, correlationId, idempotencyKey, payload, tenantId.toString());

        log.info("Relayed FHIR bundle [auditId={}, type={}, count={}, dest={}]",
                auditId, request.resourceType(), request.entries().size(), destinationUrl);
        return audit;
    }

    @Transactional
    public RelayDestinationEntity createDestination(UUID tenantId, String podId, String correlationId,
                                                      String idempotencyKey, CreateDestinationRequest request) {
        UUID destId = UUID.randomUUID();
        RelayDestinationEntity dest = new RelayDestinationEntity(destId, tenantId, request.name(),
                request.endpointUrl(), request.resourceTypes());
        if (request.authType() != null) dest.setAuthType(request.authType());
        destinationRepository.save(dest);

        appendOutboxEvent("impilo.integration.destination.created.v1", destId.toString(),
                tenantId, podId, correlationId, idempotencyKey,
                Map.of("destination_id", destId.toString(), "name", request.name(),
                        "endpoint_url", request.endpointUrl()),
                tenantId.toString());

        log.info("Created relay destination [id={}, name={}, url={}]", destId, request.name(), request.endpointUrl());
        return dest;
    }

    @Transactional(readOnly = true)
    public List<RelayDestinationEntity> listDestinations(UUID tenantId) {
        return destinationRepository.findByTenantIdAndEnabledTrue(tenantId);
    }

    @Transactional(readOnly = true)
    public Page<RelayAuditEntity> listAuditLog(UUID tenantId, int page, int size) {
        return auditRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public List<RelayAuditEntity> getAuditByBundle(String bundleId) {
        return auditRepository.findByBundleId(bundleId);
    }

    private void appendOutboxEvent(String eventType, String aggregateId, UUID tenantId,
                                    String podId, String correlationId, String idempotencyKey,
                                    Map<String, Object> payload, String partitionKey) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setAggregateType("FhirRelay");
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setCorrelationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setCausationId(correlationId != null ? UUID.fromString(correlationId) : null);
        outbox.setIdempotencyKey(idempotencyKey);
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId != null ? podId : "national-spine");
        outbox.setSubjectId(aggregateId);
        outbox.setSubjectType("FhirRelay");
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outbox.setPartitionKey(partitionKey);
        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new RuntimeException("JSON serialization failed", e); }
    }

    public static class DestinationNotFoundException extends RuntimeException {
        public DestinationNotFoundException(String msg) { super(msg); }
    }
}
