package zw.gov.mohcc.impilo.vito.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.vito.api.dto.ClientDemographicsUpdateRequest;
import zw.gov.mohcc.impilo.vito.config.VitoProperties;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ClientUpdateService {

    private final ClientRepository clientRepository;
    private final EventOutboxRepository outboxRepository;
    private final VitoProperties vitoProperties;
    private final ObjectMapper objectMapper;

    public ClientUpdateService(ClientRepository clientRepository,
                               EventOutboxRepository outboxRepository,
                               VitoProperties vitoProperties,
                               ObjectMapper objectMapper) {
        this.clientRepository = clientRepository;
        this.outboxRepository = outboxRepository;
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

        if (req.addressLine1() != null || req.city() != null || req.district() != null || req.province() != null) {
            Map<String, Object> address = new LinkedHashMap<>();
            if (req.addressLine1() != null) address.put("addressLine1", req.addressLine1());
            if (req.city() != null) address.put("city", req.city());
            if (req.district() != null) address.put("district", req.district());
            if (req.province() != null) address.put("province", req.province());
            client.setAddress(toJson(address));
        }

        client = clientRepository.save(client);

        publishOutboxEvent(client, tenantId, actorId, correlationId);

        return client;
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
