package zw.gov.mohcc.impilo.vito.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.vito.api.dto.ClientDemographicsUpdateRequest;
import zw.gov.mohcc.impilo.vito.config.VitoProperties;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientIdentifierEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientIdentifierRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ClientUpdateService {

    private static final String PASSPORT_REFERENCE = "PASSPORT_REFERENCE";

    private final ClientRepository clientRepository;
    private final EventOutboxRepository outboxRepository;
    private final ClientIdentifierRepository identifierRepository;
    private final VitoProperties vitoProperties;
    private final ObjectMapper objectMapper;

    public ClientUpdateService(ClientRepository clientRepository,
                               EventOutboxRepository outboxRepository,
                               ClientIdentifierRepository identifierRepository,
                               VitoProperties vitoProperties,
                               ObjectMapper objectMapper) {
        this.clientRepository = clientRepository;
        this.outboxRepository = outboxRepository;
        this.identifierRepository = identifierRepository;
        this.vitoProperties = vitoProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ClientEntity update(UUID tenantId,
                               UUID healthId,
                               ClientDemographicsUpdateRequest req,
                               String actorId,
                               String correlationId) {
        ClientEntity client = clientRepository.findByTenantIdAndHealthId(tenantId, healthId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Client not found: " + healthId));

        if (req.givenName() != null) client.setGivenName(req.givenName().trim());
        if (req.middleName() != null) client.setMiddleName(req.middleName().isBlank() ? null : req.middleName().trim());
        if (req.familyName() != null) client.setFamilyName(req.familyName().trim());
        if (req.dateOfBirth() != null) client.setDateOfBirth(req.dateOfBirth());
        if (req.sex() != null) client.setSex(req.sex());
        if (req.phone() != null && !req.phone().isBlank()) {
            client.setPhoneHash(hmacPhone(req.phone().trim()));
        }

        // Address: merge into existing JSON instead of replacing it, so an update
        // that touches one address field does not wipe the others.
        if (req.addressLine1() != null || req.city() != null || req.district() != null || req.province() != null) {
            Map<String, Object> address = parseMap(client.getAddress());
            if (req.addressLine1() != null) address.put("addressLine1", req.addressLine1());
            if (req.city() != null) address.put("city", req.city());
            if (req.district() != null) address.put("district", req.district());
            if (req.province() != null) address.put("province", req.province());
            client.setAddress(toJson(address));
        }

        // Extended demographics — update parity with the create path. Each field is
        // merged into its existing JSON blob (preferredLanguage/maritalStatus →
        // demographics, email/emergencyContact* → contacts) so omitted fields are
        // preserved and never silently nulled. Only rebuilt when a value is supplied.
        if (notBlank(req.preferredLanguage()) || notBlank(req.maritalStatus())) {
            Map<String, Object> demographics = parseMap(client.getDemographics());
            if (notBlank(req.preferredLanguage())) demographics.put("preferredLanguage", req.preferredLanguage().trim());
            if (notBlank(req.maritalStatus())) demographics.put("maritalStatus", req.maritalStatus().trim());
            client.setDemographics(toJson(demographics));
        }

        if (notBlank(req.email()) || notBlank(req.emergencyContactName()) || notBlank(req.emergencyContactPhone())) {
            Map<String, Object> contacts = parseMap(client.getContacts());
            if (notBlank(req.email())) contacts.put("email", req.email().trim());
            if (notBlank(req.emergencyContactName())) contacts.put("emergencyContactName", req.emergencyContactName().trim());
            if (notBlank(req.emergencyContactPhone())) contacts.put("emergencyContactPhone", req.emergencyContactPhone().trim());
            client.setContacts(toJson(contacts));
        }

        client = clientRepository.save(client);

        // passportReference is held as an identifier row (mirrors create path); upsert
        // so an update changes the existing PASSPORT_REFERENCE rather than duplicating it.
        if (notBlank(req.passportReference())) {
            upsertPassportReference(client, req.passportReference().trim());
        }

        publishOutboxEvent(client, tenantId, actorId, correlationId);

        return client;
    }

    private void upsertPassportReference(ClientEntity client, String value) {
        ClientIdentifierEntity existing = identifierRepository
                .findByTenantIdAndClientHealthId(client.getTenantId(), client.getHealthId()).stream()
                .filter(i -> PASSPORT_REFERENCE.equals(i.getIdentifierType()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            if (!value.equals(existing.getIdentifierValue())) {
                existing.setIdentifierValue(value);
                identifierRepository.save(existing);
            }
            return;
        }
        ClientIdentifierEntity identifier = new ClientIdentifierEntity();
        identifier.setTenantId(client.getTenantId());
        identifier.setClientHealthId(client.getHealthId());
        identifier.setIdentifierType(PASSPORT_REFERENCE);
        identifier.setIdentifierValue(value);
        identifier.setPrimaryFlag(false);
        identifier.setSource("DEMOGRAPHICS_UPDATE");
        identifierRepository.save(identifier);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private Map<String, Object> parseMap(String value) {
        if (value == null || value.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    private void publishOutboxEvent(ClientEntity client, UUID tenantId, String actorId, String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("healthId", client.getHealthId().toString());
        payload.put("tenantId", tenantId.toString());
        payload.put("actorId", actorId);

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("CLIENT");
        event.setAggregateId(client.getHealthId().toString());
        event.setEventType("CLIENT_DEMOGRAPHICS_UPDATED");
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
