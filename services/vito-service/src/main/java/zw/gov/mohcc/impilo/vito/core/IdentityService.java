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
    private final zw.gov.mohcc.impilo.vito.core.matching.MatchingEngine matchingEngine;
    private final zw.gov.mohcc.impilo.vito.config.VitoProperties vitoProperties;

    public IdentityService(ClientRepository clientRepository,
                           SovereignIdGenerator didGenerator,
                           ImpiloIdAliasService aliasService,
                           EventOutboxRepository outboxRepository,
                           zw.gov.mohcc.impilo.vito.core.matching.MatchingEngine matchingEngine,
                           zw.gov.mohcc.impilo.vito.config.VitoProperties vitoProperties) {
        this.clientRepository = clientRepository;
        this.didGenerator = didGenerator;
        this.aliasService = aliasService;
        this.outboxRepository = outboxRepository;
        this.matchingEngine = matchingEngine;
        this.vitoProperties = vitoProperties;
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
        // Person-scoped dedupe: a blind repeat of the same registration (double
        // submit, stateless retry) must return the existing client, not mint a
        // second PROVISIONAL person. Strong demographic match = the estate's
        // auto-link threshold via the single MatchingEngine scorer.
        ClientEntity duplicate = findStrongDuplicate(tenantId, givenName, familyName, dateOfBirth, sex);
        if (duplicate != null) {
            publishEvent("CLIENT", duplicate.getHealthId().toString(),
                    "IDENTITY_DEDUPLICATED",
                    String.format("{\"healthId\":\"%s\",\"status\":\"%s\",\"tenantId\":\"%s\",\"actorId\":\"%s\"}",
                            duplicate.getHealthId(), duplicate.getStatus(), tenantId, actorId));
            // DID is minted once at creation and not persisted — never fabricate
            // a fresh one for an existing person.
            return new ClientRegistrationResult(duplicate, null);
        }

        ClientEntity client = new ClientEntity();
        client.setTenantId(tenantId);
        client.setGivenName(givenName);
        client.setFamilyName(familyName);
        if (dateOfBirth != null) {
            client.setDateOfBirth(java.time.LocalDate.parse(dateOfBirth));
        }
        client.setSex(sex);
        client.setStatus(IdentityStatus.PROVISIONAL);
        client.setVerificationStatus(ClientVerificationState.UNVERIFIED);
        client.setIdentityAssuranceLevel(0);
        client.setActiveFlag(true);
        client.setGoldenRecordFlag(true);

        client = clientRepository.save(client);

        // Generate sovereign DID (Black Box — no PII input)
        String did = didGenerator.generate(client.getHealthId());

        // Note: IMPILO_ID alias is NOT created at registration time.
        // The human-facing Impilo ID (#########X) is generated later during
        // IssuanceStateMachineService.issue() when the issuance is approved.

        // Publish IDENTITY_CREATED event
        publishEvent("CLIENT", client.getHealthId().toString(),
                "IDENTITY_CREATED",
                String.format("{\"healthId\":\"%s\",\"status\":\"PROVISIONAL\",\"did\":\"%s\",\"tenantId\":\"%s\"}",
                        client.getHealthId(), did, client.getTenantId()));

        return new ClientRegistrationResult(client, did);
    }

    /**
     * Best strong-match candidate for a registration, or null when nothing
     * reaches the auto-link threshold. Uses the blocking query (birth year /
     * family initial) so the scorer runs over a small candidate set; tombstoned
     * and deceased records never dedupe.
     */
    private ClientEntity findStrongDuplicate(UUID tenantId, String givenName,
                                             String familyName, String dateOfBirth, String sex) {
        // Evidence floor: the renormalized demographic score is only
        // auto-link-grade when ALL four fields are present — a perfect match
        // on partial evidence must mint, not absorb.
        boolean hasFamily = familyName != null && !familyName.isBlank();
        if (givenName == null || givenName.isBlank() || !hasFamily
                || dateOfBirth == null || sex == null || sex.isBlank()) {
            return null;
        }
        Integer birthYear = null;
        if (dateOfBirth != null) {
            try {
                birthYear = java.time.LocalDate.parse(dateOfBirth).getYear();
            } catch (java.time.format.DateTimeParseException ignored) {
                // register() itself will reject the malformed date; no blocking key here.
            }
        }
        String familyInitial = hasFamily ? familyName.substring(0, 1).toLowerCase() : null;
        if (birthYear == null && familyInitial == null) {
            return null;
        }

        double threshold = vitoProperties.getMatching().getThresholdAutoLink();
        ClientEntity best = null;
        double bestScore = 0.0;
        for (ClientEntity candidate : clientRepository.findBlockingCandidates(
                tenantId, UUID.randomUUID(), birthYear, familyInitial)) {
            if (candidate.getStatus() == IdentityStatus.MERGED
                    || candidate.getStatus() == IdentityStatus.DECEASED) {
                continue;
            }
            double score = matchingEngine.scoreDemographics(givenName, familyName, dateOfBirth, sex, candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return bestScore >= threshold ? best : null;
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
        client.setVerificationStatus(ClientVerificationState.VERIFIED);
        client.setIdentityAssuranceLevel(Math.max(2, client.getIdentityAssuranceLevel()));
        client = clientRepository.save(client);

        publishEvent("CLIENT", healthId.toString(), "IDENTITY_VERIFIED",
                String.format("{\"healthId\":\"%s\",\"verifiedBy\":\"%s\",\"tenantId\":\"%s\"}",
                        healthId, verifiedBy, tenantId));

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
        client.setDeceasedFlag(true);
        client.setActiveFlag(false);
        client = clientRepository.save(client);

        // Revoke all aliases
        aliasService.revokeAll(tenantId, healthId);

        publishEvent("CLIENT", healthId.toString(), "IDENTITY_DECEASED",
                String.format("{\"healthId\":\"%s\",\"deathNotificationRef\":\"%s\",\"tenantId\":\"%s\"}",
                        healthId, deathNotificationRef, tenantId));

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
        client.setActiveFlag(false);
        return clientRepository.save(client);
    }

    @Transactional(readOnly = true)
    public Optional<ClientEntity> findByHealthId(UUID tenantId, UUID healthId) {
        return clientRepository.findByTenantIdAndHealthId(tenantId, healthId);
    }

    /**
     * Resolve a client by their Impilo ID via the alias vault (Identity Contract §6).
     * The impilo_id column is now encrypted at rest, so a WHERE impilo_id = ? query
     * cannot match; the vault's HMAC lookup hash + Argon2id verifier is the index.
     */
    @Transactional(readOnly = true)
    public Optional<ClientEntity> findByImpiloId(UUID tenantId, String impiloId) {
        if (impiloId == null || impiloId.isBlank()) {
            return Optional.empty();
        }
        return aliasService.resolve(tenantId, "IMPILO_ID", impiloId)
                .flatMap(healthId -> clientRepository.findByTenantIdAndHealthId(tenantId, healthId));
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
