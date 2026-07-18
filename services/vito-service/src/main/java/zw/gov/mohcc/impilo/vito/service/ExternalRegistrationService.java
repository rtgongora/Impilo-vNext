package zw.gov.mohcc.impilo.vito.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vito.api.dto.ExternalClientRegistrationRequest;
import zw.gov.mohcc.impilo.vito.api.dto.ExternalClientRegistrationResponse;
import zw.gov.mohcc.impilo.vito.config.VitoProperties;
import zw.gov.mohcc.impilo.vito.events.ClientAddressDelegationPublisher;
import zw.gov.mohcc.impilo.vito.core.ClientVerificationState;
import zw.gov.mohcc.impilo.vito.core.IdentityStatus;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientIdentifierEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.core.PhidPoolService;
import zw.gov.mohcc.impilo.vito.persistence.entity.PhidPoolEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientIdentifierRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.PhidPoolRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExternalRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(ExternalRegistrationService.class);

    private final ClientRepository clientRepository;
    private final ClientIdentifierRepository clientIdentifierRepository;
    private final EventOutboxRepository outboxRepository;
    private final VitoProperties vitoProperties;
    private final ObjectMapper objectMapper;
    private final PhidPoolRepository phidPoolRepository;
    private final PhidPoolService phidPoolService;
    private final ClientAddressDelegationPublisher addressDelegationPublisher;
    private final zw.gov.mohcc.impilo.vito.core.matching.MatchingEngine matchingEngine;
    private final zw.gov.mohcc.impilo.vito.core.ContactHasher contactHasher;

    public ExternalRegistrationService(zw.gov.mohcc.impilo.vito.core.matching.MatchingEngine matchingEngine,
                                       zw.gov.mohcc.impilo.vito.core.ContactHasher contactHasher,
                                       ClientRepository clientRepository,
                                       ClientIdentifierRepository clientIdentifierRepository,
                                       EventOutboxRepository outboxRepository,
                                       VitoProperties vitoProperties,
                                       ObjectMapper objectMapper,
                                       PhidPoolRepository phidPoolRepository,
                                       PhidPoolService phidPoolService,
                                       ClientAddressDelegationPublisher addressDelegationPublisher) {
        this.matchingEngine = matchingEngine;
        this.contactHasher = contactHasher;
        this.clientRepository = clientRepository;
        this.clientIdentifierRepository = clientIdentifierRepository;
        this.outboxRepository = outboxRepository;
        this.vitoProperties = vitoProperties;
        this.objectMapper = objectMapper;
        this.phidPoolRepository = phidPoolRepository;
        this.phidPoolService = phidPoolService;
        this.addressDelegationPublisher = addressDelegationPublisher;
    }

    @Transactional
    public ExternalClientRegistrationResponse register(UUID tenantId,
                                                       ExternalClientRegistrationRequest request,
                                                       String actorId,
                                                       String actorType,
                                                       String correlationId) {
        if (!"SYSTEM".equalsIgnoreCase(actorType)) {
            throw new AccessDeniedException("External client registration requires SYSTEM actor type");
        }

        if (request.nationalId() != null && !request.nationalId().isBlank()) {
            Optional<ClientIdentifierEntity> existing = clientIdentifierRepository
                    .findByTenantIdAndIdentifierTypeAndIdentifierValue(tenantId, "NID", request.nationalId().trim());
            if (existing.isPresent()) {
                UUID existingHealthId = existing.get().getClientHealthId();
                ClientEntity existingClient = clientRepository.findByTenantIdAndHealthId(tenantId, existingHealthId)
                        .orElse(null);
                String impiloId = existingClient != null ? existingClient.getImpiloId() : null;
                persistMrsPhidIdentifier(existingHealthId, request.mrsPhid(), tenantId, request.sourceSystem());
                return new ExternalClientRegistrationResponse(
                        existingHealthId,
                        impiloId,
                        "DEDUPLICATED",
                        "EXISTING",
                        correlationId
                );
            }
        }

        List<ClientEntity> candidates = clientRepository.findTop100ByTenantIdOrderByUpdatedAtDesc(tenantId);
        double bestScore = 0.0;
        ClientEntity bestMatch = null;

        for (ClientEntity candidate : candidates) {
            double score = matchingEngine.scoreDemographics(request.givenName(), request.familyName(),
                    request.dateOfBirth() != null ? request.dateOfBirth().toString() : null,
                    request.sex(), candidate);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = candidate;
            }
        }

        double autoLinkThreshold = vitoProperties.getMatching().getThresholdAutoLink();
        double reviewThreshold = vitoProperties.getMatching().getThresholdManualReview();

        if (bestScore >= autoLinkThreshold && bestMatch != null) {
            persistMrsPhidIdentifier(bestMatch.getHealthId(), request.mrsPhid(), tenantId, request.sourceSystem());
            return new ExternalClientRegistrationResponse(
                    bestMatch.getHealthId(),
                    bestMatch.getImpiloId(),
                    "DEDUPLICATED",
                    "EXISTING",
                    correlationId
            );
        }

        if (bestScore >= reviewThreshold && bestMatch != null) {
            ClientEntity provisional = buildNewClient(tenantId, request, actorId);
            applyPreAllocatedHealthId(provisional, request.preAllocatedHealthId(), request.sourceSystemPatientId());
            provisional.setStatus(IdentityStatus.PROVISIONAL);
            provisional = clientRepository.save(provisional);
            addressDelegationPublisher.emitAddressUpserted(provisional);
            persistNidIdentifier(provisional, request, tenantId);
            persistMrsPhidIdentifier(provisional.getHealthId(), request.mrsPhid(), tenantId, request.sourceSystem());
            publishOutboxEvent(provisional, request, tenantId, correlationId, "PENDING_REVIEW");
            return new ExternalClientRegistrationResponse(
                    provisional.getHealthId(),
                    provisional.getImpiloId(),
                    "PROVISIONAL_REVIEW",
                    "PENDING_REVIEW",
                    correlationId
            );
        }

        ClientEntity newClient = buildNewClient(tenantId, request, actorId);
        applyPreAllocatedHealthId(newClient, request.preAllocatedHealthId(), request.sourceSystemPatientId());
        newClient = clientRepository.save(newClient);
        addressDelegationPublisher.emitAddressUpserted(newClient);
        persistNidIdentifier(newClient, request, tenantId);
        persistMrsPhidIdentifier(newClient.getHealthId(), request.mrsPhid(), tenantId, request.sourceSystem());
        publishOutboxEvent(newClient, request, tenantId, correlationId, "CREATED");
        return new ExternalClientRegistrationResponse(
                newClient.getHealthId(),
                newClient.getImpiloId(),
                "REGISTERED",
                "CREATED",
                correlationId
        );
    }

    private ClientEntity buildNewClient(UUID tenantId, ExternalClientRegistrationRequest request, String actorId) {
        ClientEntity client = new ClientEntity();
        client.setTenantId(tenantId);
        client.setGivenName(request.givenName().trim());
        client.setMiddleName(request.middleName() != null && !request.middleName().isBlank()
                ? request.middleName().trim() : null);
        client.setFamilyName(request.familyName().trim());
        client.setDateOfBirth(request.dateOfBirth());
        client.setSex(request.sex());
        client.setStatus(IdentityStatus.PROVISIONAL);
        client.setVerificationStatus(ClientVerificationState.UNVERIFIED);
        client.setIdentityAssuranceLevel(0);
        client.setActiveFlag(true);
        client.setGoldenRecordFlag(true);
        client.setSourceSystem(request.sourceSystem());
        client.setSourceSystemPatientId(request.sourceSystemPatientId());

        if (request.phone() != null && !request.phone().isBlank()) {
            client.setPhoneHash(contactHasher.hashPhone(request.phone()));
        }

        Map<String, Object> address = new LinkedHashMap<>();
        if (request.addressLine1() != null) address.put("addressLine1", request.addressLine1());
        if (request.city() != null) address.put("city", request.city());
        if (request.district() != null) address.put("district", request.district());
        if (request.province() != null) address.put("province", request.province());
        if (!address.isEmpty()) {
            client.setAddress(toJson(address));
        }

        Map<String, Object> contacts = new LinkedHashMap<>();
        if (request.phone() != null && !request.phone().isBlank()) contacts.put("phone", request.phone().trim());
        if (!contacts.isEmpty()) {
            client.setContacts(toJson(contacts));
        }

        Map<String, Object> demographics = new LinkedHashMap<>();
        demographics.put("sourceSystem", request.sourceSystem());
        if (request.sourceSystemPatientId() != null) demographics.put("sourceSystemPatientId", request.sourceSystemPatientId());
        if (request.facilityId() != null) demographics.put("facilityId", request.facilityId());
        client.setDemographics(toJson(demographics));

        return client;
    }

    private void persistNidIdentifier(ClientEntity client, ExternalClientRegistrationRequest request, UUID tenantId) {
        if (request.nationalId() == null || request.nationalId().isBlank()) return;
        ClientIdentifierEntity identifier = new ClientIdentifierEntity();
        identifier.setTenantId(tenantId);
        identifier.setClientHealthId(client.getHealthId());
        identifier.setIdentifierType("NID");
        identifier.setIdentifierValue(request.nationalId().trim());
        identifier.setPrimaryFlag(true);
        identifier.setSourceSystem(request.sourceSystem());
        clientIdentifierRepository.save(identifier);
    }

    private void persistMrsPhidIdentifier(UUID clientHealthId, String mrsPhid, UUID tenantId, String sourceSystem) {
        if (mrsPhid == null || mrsPhid.isBlank()) return;
        ClientIdentifierEntity identifier = new ClientIdentifierEntity();
        identifier.setTenantId(tenantId);
        identifier.setClientHealthId(clientHealthId);
        identifier.setIdentifierType("MRS_PHID");
        identifier.setIdentifierValue(mrsPhid.trim());
        identifier.setPrimaryFlag(false);
        identifier.setSourceSystem(sourceSystem);
        clientIdentifierRepository.save(identifier);
    }

    private void applyPreAllocatedHealthId(ClientEntity client, UUID preAllocatedHealthId, String mrsPatientId) {
        if (preAllocatedHealthId == null) return;
        Optional<PhidPoolEntity> entry = phidPoolRepository.findByHealthId(preAllocatedHealthId);
        if (entry.isPresent() && "AVAILABLE".equals(entry.get().getStatus())) {
            client.setHealthId(preAllocatedHealthId);
            phidPoolService.claim(preAllocatedHealthId, mrsPatientId);
        } else {
            String reason = entry.isPresent()
                    ? "pool entry status=" + entry.get().getStatus()
                    : "pool entry not found";
            log.warn("Pre-allocated healthId {} could not be used ({}); client will receive a new random UUID. " +
                    "MRS mrsPatientId={} will have a diverged identity until reconciled.",
                    preAllocatedHealthId, reason, mrsPatientId);
        }
    }

    private void publishOutboxEvent(ClientEntity client,
                                    ExternalClientRegistrationRequest request,
                                    UUID tenantId,
                                    String correlationId,
                                    String syncStatus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("healthId", client.getHealthId().toString());
        payload.put("tenantId", tenantId.toString());
        payload.put("syncStatus", syncStatus);
        payload.put("sourceSystem", request.sourceSystem());
        if (request.sourceSystemPatientId() != null) {
            payload.put("sourceSystemPatientId", request.sourceSystemPatientId());
        }

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("CLIENT");
        event.setAggregateId(client.getHealthId().toString());
        event.setEventType("EXTERNAL_CLIENT_REGISTERED");
        event.setPayload(toJson(payload));
        event.setTenantId(tenantId.toString());
        event.setCorrelationId(correlationId);
        event.setSubjectType("CLIENT");
        event.setSubjectId(client.getHealthId().toString());
        outboxRepository.save(event);
    }

    private String hmacPhone(String phone) {
        try {
            String pepper = vitoProperties.getHmac().getPepper();
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(
                    pepper.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(phone.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
