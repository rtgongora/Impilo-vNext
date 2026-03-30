package zw.gov.mohcc.impilo.vito.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.sharedkernel.events.DeltaPayload;
import zw.gov.mohcc.impilo.vito.core.did.SovereignIdGenerator;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Core identity management service.
 *
 * Implements WHO SMART Guidelines L3/L4:
 *   - PROVISIONAL: identity issued with minimal proofing (offline, rapid registration)
 *   - VERIFIED: proofing complete (biometric capture, document verification)
 *   - ACTIVE: verified and in good standing
 *   - INACTIVE: temporarily suspended
 *   - DECEASED: terminal state (from UBOMI CRVS notification)
 *   - MERGED: record merged into another (soft tombstone)
 *
 * Every identity creation generates a did:impilo DID via the Black Box generator.
 */
@Service
public class IdentityService {

    private static final Logger log = LoggerFactory.getLogger(IdentityService.class);

    private final ClientRepository clientRepository;
    private final SovereignIdGenerator didGenerator;
    private final ImpiloIdAliasService aliasService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public IdentityService(ClientRepository clientRepository,
                           SovereignIdGenerator didGenerator,
                           ImpiloIdAliasService aliasService,
                           EventOutboxRepository outboxRepository,
                           ObjectMapper objectMapper) {
        this.clientRepository = clientRepository;
        this.didGenerator = didGenerator;
        this.aliasService = aliasService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Register a new client identity.
     * Status starts as PROVISIONAL per WHO SMART L3 guidelines.
     *
     * @return the created client with health_id and generated DID
     */
    @Transactional
    public ClientRegistrationResult register(UUID tenantId, String givenName,
                                              String familyName, String dateOfBirth,
                                              String sex, String actorId) {
        ClientEntity client = new ClientEntity();
        client.setTenantId(tenantId);
        client.setGivenName(givenName);
        client.setFamilyName(familyName);
        if (dateOfBirth != null) {
            client.setDateOfBirth(java.time.LocalDate.parse(dateOfBirth));
        }
        client.setSex(sex);
        client.setStatus(IdentityStatus.PROVISIONAL);

        client = clientRepository.save(client);

        // Generate sovereign DID (Black Box — no PII input)
        String did = didGenerator.generate(client.getHealthId());

        // Note: IMPILO_ID alias is NOT created at registration time.
        // The human-facing Impilo ID (#########X) is generated later during
        // IssuanceStateMachineService.issue() when the issuance is approved.

        // Publish IDENTITY_CREATED event
        publishEvent("CLIENT", client.getHealthId().toString(),
                "IDENTITY_CREATED",
                String.format("{\"healthId\":\"%s\",\"status\":\"PROVISIONAL\",\"did\":\"%s\"}",
                        client.getHealthId(), did));

        return new ClientRegistrationResult(client, did);
    }

    /**
     * Register a client identity that originated from the experience-bff.
     *
     * <p>Uses the {@code bff_patient_id} as the authoritative CRID so the record can be
     * correlated back to the provisional patient in the BFF.  If a client with the same
     * CRID already exists the call is a no-op (idempotent on replay).</p>
     *
     * <p>Publishes a rich {@code IDENTITY_CREATED} event that includes all demographic
     * fields, allowing the BFF {@code PatientEventConsumer} to update the provisional
     * patient record with the authoritative VITO {@code healthId} as its CPID.</p>
     *
     * @param bffPatientId UUID from the BFF — used as CRID for correlation
     * @param tenantUuid   deterministic tenant UUID derived from the tenant string
     * @param tenantLabel  original string tenant label (e.g. "moh-zw") stored in event for BFF correlation
     * @return created (or existing) client
     */
    @Transactional
    public ClientRegistrationResult registerFromExperience(UUID bffPatientId,
                                                            UUID tenantUuid,
                                                            String tenantLabel,
                                                            String givenName,
                                                            String familyName,
                                                            String dateOfBirth,
                                                            String sex) {
        return clientRepository.findByTenantIdAndCrid(tenantUuid, bffPatientId)
                .map(existing -> {
                    log.info("IdentityService.registerFromExperience: idempotent skip, crid={} already exists as healthId={}", bffPatientId, existing.getHealthId());
                    return new ClientRegistrationResult(existing, "");
                })
                .orElseGet(() -> {
                    ClientEntity client = new ClientEntity();
                    client.setCrid(bffPatientId);
                    client.setTenantId(tenantUuid);
                    client.setGivenName(givenName != null ? givenName : "Unknown");
                    client.setFamilyName(familyName != null ? familyName : "Unknown");
                    if (dateOfBirth != null && !dateOfBirth.isBlank()) {
                        try {
                            client.setDateOfBirth(java.time.LocalDate.parse(dateOfBirth.substring(0, 10)));
                        } catch (Exception e) {
                            log.warn("IdentityService.registerFromExperience: could not parse dob '{}': {}", dateOfBirth, e.getMessage());
                        }
                    }
                    client.setSex(sex);
                    client.setStatus(IdentityStatus.PROVISIONAL);

                    client = clientRepository.save(client);
                    String did = didGenerator.generate(client.getHealthId());

                    Map<String, Object> eventPayload = clientToMap(client);
                    eventPayload.put("did", did);
                    eventPayload.put("tenant_label", tenantLabel);

                    publishEvent("CLIENT", client.getHealthId().toString(), "IDENTITY_CREATED", eventPayload);

                    log.info("IdentityService.registerFromExperience: registered healthId={} crid={} tenant={}", client.getHealthId(), bffPatientId, tenantLabel);
                    return new ClientRegistrationResult(client, did);
                });
    }

    /**
     * Promote a PROVISIONAL identity to VERIFIED after proofing.
     */
    @Transactional
    public ClientEntity verify(UUID tenantId, UUID healthId, String verifiedBy) {
        ClientEntity client = clientRepository.findByTenantIdAndHealthId(tenantId, healthId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        if (client.getStatus() != IdentityStatus.PROVISIONAL) {
            throw new IllegalStateException("Only PROVISIONAL clients can be verified, current: " + client.getStatus());
        }

        Map<String, Object> before = clientToMap(client);

        client.setStatus(IdentityStatus.VERIFIED);
        client = clientRepository.save(client);

        Map<String, Object> after = clientToMap(client);

        DeltaPayload delta = DeltaPayload.of(before, after, List.of("status"));
        publishEvent("CLIENT", healthId.toString(), "IDENTITY_VERIFIED",
                Map.of("delta", delta.toMap(), "full", after, "verifiedBy", verifiedBy));

        return client;
    }

    /**
     * Mark a client as DECEASED (triggered by UBOMI death notification).
     */
    @Transactional
    public ClientEntity markDeceased(UUID tenantId, UUID healthId, String deathNotificationRef) {
        ClientEntity client = clientRepository.findByTenantIdAndHealthId(tenantId, healthId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        Map<String, Object> before = clientToMap(client);

        client.setStatus(IdentityStatus.DECEASED);
        client = clientRepository.save(client);

        Map<String, Object> after = clientToMap(client);

        // Revoke all aliases
        aliasService.revokeAll(tenantId, healthId);

        DeltaPayload delta = DeltaPayload.of(before, after, List.of("status"));
        publishEvent("CLIENT", healthId.toString(), "IDENTITY_DECEASED",
                Map.of("delta", delta.toMap(), "full", after, "deathNotificationRef", deathNotificationRef));

        return client;
    }

    /**
     * Deactivate a client (temporary suspension).
     */
    @Transactional
    public ClientEntity deactivate(UUID tenantId, UUID healthId, String reason) {
        ClientEntity client = clientRepository.findByTenantIdAndHealthId(tenantId, healthId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        Map<String, Object> before = clientToMap(client);

        client.setStatus(IdentityStatus.INACTIVE);
        client = clientRepository.save(client);

        Map<String, Object> after = clientToMap(client);

        DeltaPayload delta = DeltaPayload.of(before, after, List.of("status"));
        publishEvent("CLIENT", healthId.toString(), "IDENTITY_DEACTIVATED",
                Map.of("delta", delta.toMap(), "full", after, "reason", reason));

        return client;
    }

    @Transactional(readOnly = true)
    public Optional<ClientEntity> findByHealthId(UUID tenantId, UUID healthId) {
        return clientRepository.findByTenantIdAndHealthId(tenantId, healthId);
    }

    @Transactional(readOnly = true)
    public Page<ClientEntity> listClients(UUID tenantId, IdentityStatus status, Pageable pageable) {
        if (status != null) {
            return clientRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        return clientRepository.findByTenantId(tenantId, pageable);
    }

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, Map<String, Object> payloadMap) {
        try {
            String json = objectMapper.registerModule(new JavaTimeModule()).writeValueAsString(payloadMap);
            publishEvent(aggregateType, aggregateId, eventType, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event payload for {}/{}", aggregateType, aggregateId, e);
            throw new RuntimeException("Event serialization failed", e);
        }
    }

    private Map<String, Object> clientToMap(ClientEntity c) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", c.getId());
        map.put("health_id", c.getHealthId());
        map.put("tenant_id", c.getTenantId());
        map.put("given_name", c.getGivenName());
        map.put("family_name", c.getFamilyName());
        map.put("date_of_birth", c.getDateOfBirth());
        map.put("sex", c.getSex());
        map.put("status", c.getStatus() != null ? c.getStatus().name() : null);
        map.put("crid", c.getCrid());
        return map;
    }

    /**
     * Result of client registration: the client entity + generated DID.
     */
    public record ClientRegistrationResult(ClientEntity client, String did) {}
}
