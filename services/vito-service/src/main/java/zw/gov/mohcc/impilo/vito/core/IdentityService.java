package zw.gov.mohcc.impilo.vito.core;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.core.did.SovereignIdGenerator;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

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

    private final ClientRepository clientRepository;
    private final SovereignIdGenerator didGenerator;
    private final ImpiloIdAliasService aliasService;
    private final EventOutboxRepository outboxRepository;

    public IdentityService(ClientRepository clientRepository,
                           SovereignIdGenerator didGenerator,
                           ImpiloIdAliasService aliasService,
                           EventOutboxRepository outboxRepository) {
        this.clientRepository = clientRepository;
        this.didGenerator = didGenerator;
        this.aliasService = aliasService;
        this.outboxRepository = outboxRepository;
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
     * Promote a PROVISIONAL identity to VERIFIED after proofing.
     */
    @Transactional
    public ClientEntity verify(UUID tenantId, UUID healthId, String verifiedBy) {
        ClientEntity client = clientRepository.findByTenantIdAndHealthId(tenantId, healthId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        if (client.getStatus() != IdentityStatus.PROVISIONAL) {
            throw new IllegalStateException("Only PROVISIONAL clients can be verified, current: " + client.getStatus());
        }

        client.setStatus(IdentityStatus.VERIFIED);
        client = clientRepository.save(client);

        publishEvent("CLIENT", healthId.toString(), "IDENTITY_VERIFIED",
                String.format("{\"healthId\":\"%s\",\"verifiedBy\":\"%s\"}", healthId, verifiedBy));

        return client;
    }

    /**
     * Mark a client as DECEASED (triggered by UBOMI death notification).
     */
    @Transactional
    public ClientEntity markDeceased(UUID tenantId, UUID healthId, String deathNotificationRef) {
        ClientEntity client = clientRepository.findByTenantIdAndHealthId(tenantId, healthId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        client.setStatus(IdentityStatus.DECEASED);
        client = clientRepository.save(client);

        // Revoke all aliases
        aliasService.revokeAll(tenantId, healthId);

        publishEvent("CLIENT", healthId.toString(), "IDENTITY_DECEASED",
                String.format("{\"healthId\":\"%s\",\"deathNotificationRef\":\"%s\"}",
                        healthId, deathNotificationRef));

        return client;
    }

    /**
     * Deactivate a client (temporary suspension).
     */
    @Transactional
    public ClientEntity deactivate(UUID tenantId, UUID healthId, String reason) {
        ClientEntity client = clientRepository.findByTenantIdAndHealthId(tenantId, healthId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        client.setStatus(IdentityStatus.INACTIVE);
        return clientRepository.save(client);
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

    /**
     * Result of client registration: the client entity + generated DID.
     */
    public record ClientRegistrationResult(ClientEntity client, String did) {}
}
