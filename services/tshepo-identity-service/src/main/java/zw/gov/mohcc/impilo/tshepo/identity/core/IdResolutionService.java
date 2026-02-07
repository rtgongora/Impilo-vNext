package zw.gov.mohcc.impilo.tshepo.identity.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.*;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.IdMappingEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.IdMappingRepository;

import java.util.Map;
import java.util.UUID;

/**
 * Core identity resolution logic.
 *
 * <h3>Resolve chain:</h3>
 * <ol>
 *   <li>Impilo ID hash → VITO REST call → Health ID (VITO resolves the hash; we never store the plaintext Impilo ID)</li>
 *   <li>Health ID → local id_mapping table → CPID</li>
 *   <li>CPID → reverse lookup via id_mapping</li>
 * </ol>
 *
 * <p>All lookups are tenant-scoped. If resolution fails at any step, a generic
 * 404 is returned (default deny, no enumeration hints).</p>
 */
@Service
public class IdResolutionService {

    private static final Logger log = LoggerFactory.getLogger(IdResolutionService.class);

    private final IdMappingRepository mappingRepo;
    private final EventOutboxRepository outboxRepo;
    private final CpidGenerator cpidGenerator;
    private final RestTemplate vitoRestTemplate;
    private final ObjectMapper objectMapper;

    public IdResolutionService(IdMappingRepository mappingRepo,
                                EventOutboxRepository outboxRepo,
                                CpidGenerator cpidGenerator,
                                @Qualifier("vitoRestTemplate") RestTemplate vitoRestTemplate,
                                ObjectMapper objectMapper) {
        this.mappingRepo = mappingRepo;
        this.outboxRepo = outboxRepo;
        this.cpidGenerator = cpidGenerator;
        this.vitoRestTemplate = vitoRestTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Full resolution: Impilo ID hash → Health ID (via VITO) → CPID.
     *
     * <p>Calls VITO to resolve the Impilo ID hash to a Health ID, then looks
     * up or creates the CPID mapping locally.</p>
     */
    @Transactional
    public ResolveResponse resolve(ResolveRequest request) {
        UUID healthId = resolveImpiloIdToHealthId(request.tenantId(), request.impiloIdHash());
        IdMappingEntity mapping = findOrCreateMapping(request.tenantId(), healthId, null);
        return new ResolveResponse(
                mapping.getHealthId(),
                mapping.getCpid(),
                mapping.getCrid(),
                mapping.getMappingStatus()
        );
    }

    /**
     * Look up the CPID for a given Health ID within a tenant.
     */
    @Transactional(readOnly = true)
    public MappingResponse getMappingByHealthId(UUID tenantId, UUID healthId) {
        IdMappingEntity mapping = mappingRepo.findByTenantIdAndHealthId(tenantId, healthId)
                .orElseThrow(() -> new IdentityNotFoundException("Identity not found"));
        return toMappingResponse(mapping);
    }

    /**
     * Reverse lookup: CPID → Health ID within a tenant.
     */
    @Transactional(readOnly = true)
    public MappingResponse getMappingByCpid(UUID tenantId, UUID cpid) {
        IdMappingEntity mapping = mappingRepo.findByTenantIdAndCpid(tenantId, cpid)
                .orElseThrow(() -> new IdentityNotFoundException("Identity not found"));
        return toMappingResponse(mapping);
    }

    /**
     * Create a new Health ID → CPID mapping. The CPID is deterministically
     * generated if one does not already exist.
     */
    @Transactional
    public MappingResponse createMapping(CreateMappingRequest request) {
        IdMappingEntity mapping = findOrCreateMapping(
                request.tenantId(), request.healthId(), request.crid());
        return toMappingResponse(mapping);
    }

    // ── Internal methods ────────────────────────────────────────────────────

    /**
     * Calls VITO to resolve an Impilo ID hash to a Health ID.
     * VITO endpoint: POST /v1/registry/resolve with body { "lookupHash": "..." }
     * Expected response: { "data": { "healthId": "uuid" } }
     */
    private UUID resolveImpiloIdToHealthId(UUID tenantId, String impiloIdHash) {
        try {
            Map<String, Object> body = Map.of(
                    "tenantId", tenantId.toString(),
                    "lookupHash", impiloIdHash
            );
            ResponseEntity<String> response = vitoRestTemplate.postForEntity(
                    "/v1/registry/resolve", body, String.class);

            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new IdentityNotFoundException("Identity not found");
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode dataNode = root.path("data").path("healthId");
            if (dataNode.isMissingNode() || dataNode.isNull()) {
                throw new IdentityNotFoundException("Identity not found");
            }

            return UUID.fromString(dataNode.asText());
        } catch (HttpClientErrorException.NotFound e) {
            throw new IdentityNotFoundException("Identity not found");
        } catch (IdentityNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to resolve Impilo ID hash via VITO: {}", e.getMessage());
            throw new IdentityNotFoundException("Identity not found");
        }
    }

    /**
     * Finds an existing mapping or creates a new one with a deterministic CPID.
     */
    private IdMappingEntity findOrCreateMapping(UUID tenantId, UUID healthId, UUID crid) {
        return mappingRepo.findByTenantIdAndHealthId(tenantId, healthId)
                .orElseGet(() -> {
                    UUID cpid = cpidGenerator.generateCpid(tenantId, healthId);

                    IdMappingEntity entity = new IdMappingEntity();
                    entity.setTenantId(tenantId);
                    entity.setHealthId(healthId);
                    entity.setCpid(cpid);
                    entity.setCrid(crid);
                    entity.setMappingStatus("ACTIVE");

                    IdMappingEntity saved = mappingRepo.save(entity);

                    // Publish event to outbox
                    publishOutboxEvent("IdMapping", cpid.toString(), "MAPPING_CREATED",
                            Map.of("tenantId", tenantId, "healthId", healthId, "cpid", cpid));

                    log.info("Created id_mapping: tenant={}, healthId={}, cpid={}",
                            tenantId, healthId, cpid);
                    return saved;
                });
    }

    private MappingResponse toMappingResponse(IdMappingEntity entity) {
        return new MappingResponse(
                entity.getHealthId(),
                entity.getCpid(),
                entity.getCrid(),
                entity.getMappingStatus(),
                entity.getCreatedAt()
        );
    }

    private void publishOutboxEvent(String aggregateType, String aggregateId,
                                     String eventType, Object payload) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            outboxRepo.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", e.getMessage());
        }
    }
}
