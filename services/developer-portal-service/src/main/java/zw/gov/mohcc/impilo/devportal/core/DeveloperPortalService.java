package zw.gov.mohcc.impilo.devportal.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.devportal.api.dto.IssueKeyRequest;
import zw.gov.mohcc.impilo.devportal.api.dto.RegisterClientRequest;
import zw.gov.mohcc.impilo.devportal.api.dto.RotateKeyRequest;
import zw.gov.mohcc.impilo.devportal.domain.ApiKeyEntity;
import zw.gov.mohcc.impilo.devportal.domain.ClientEntity;
import zw.gov.mohcc.impilo.devportal.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.devportal.repository.ApiKeyRepository;
import zw.gov.mohcc.impilo.devportal.repository.ClientRepository;
import zw.gov.mohcc.impilo.devportal.repository.OutboxEventRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class DeveloperPortalService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String KEY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final ClientRepository clientRepo;
    private final ApiKeyRepository keyRepo;
    private final OutboxEventRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public DeveloperPortalService(ClientRepository clientRepo, ApiKeyRepository keyRepo,
                                   OutboxEventRepository outboxRepo, ObjectMapper objectMapper) {
        this.clientRepo = clientRepo;
        this.keyRepo = keyRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> registerClient(UUID tenantId, String correlationId,
                                               String idempotencyKey, RegisterClientRequest request) {
        ClientEntity client = new ClientEntity();
        client.setTenantId(tenantId);
        client.setClientName(request.clientName());
        client.setDescription(request.description());
        client.setContactEmail(request.contactEmail());
        client.setSandboxEnabled(request.sandboxEnabled());
        client = clientRepo.save(client);

        emitOutboxEvent("DevClient", client.getId().toString(),
                "impilo.developer-portal.client.registered.v1",
                tenantId, correlationId, idempotencyKey,
                client.getId().toString(), "DevClient",
                Map.of("client_id", client.getId().toString(),
                        "client_name", client.getClientName(),
                        "sandbox_enabled", client.isSandboxEnabled()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("client_id", client.getId());
        response.put("client_name", client.getClientName());
        response.put("status", client.getStatus());
        response.put("sandbox_enabled", client.isSandboxEnabled());
        response.put("created_at", client.getCreatedAt().toString());
        return response;
    }

    public Optional<ClientEntity> getClient(UUID clientId) {
        return clientRepo.findById(clientId);
    }

    public List<ClientEntity> listClients(UUID tenantId) {
        return clientRepo.findByTenantId(tenantId);
    }

    @Transactional
    public Map<String, Object> issueApiKey(UUID clientId, UUID tenantId, String correlationId,
                                            String idempotencyKey, IssueKeyRequest request) {
        ClientEntity client = clientRepo.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));

        String rawKey = generateApiKey();
        String prefix = rawKey.substring(0, 8);
        String hash = sha256(rawKey);

        ApiKeyEntity key = new ApiKeyEntity();
        key.setClientId(clientId);
        key.setKeyPrefix(prefix);
        key.setKeyHash(hash);
        key.setLabel(request.label());
        if (request.expiresInDays() != null) {
            key.setExpiresAt(OffsetDateTime.now().plusDays(request.expiresInDays()));
        }
        key = keyRepo.save(key);

        emitOutboxEvent("ApiKey", key.getId().toString(),
                "impilo.developer-portal.apikey.issued.v1",
                tenantId, correlationId, idempotencyKey,
                key.getId().toString(), "ApiKey",
                Map.of("key_id", key.getId().toString(),
                        "client_id", clientId.toString(),
                        "key_prefix", prefix));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("key_id", key.getId());
        response.put("api_key", rawKey); // Only returned once at issuance
        response.put("key_prefix", prefix);
        response.put("label", key.getLabel());
        response.put("status", key.getStatus());
        response.put("created_at", key.getCreatedAt().toString());
        return response;
    }

    @Transactional
    public Map<String, Object> rotateApiKey(UUID keyId, UUID tenantId, String correlationId,
                                             String idempotencyKey, RotateKeyRequest request) {
        ApiKeyEntity oldKey = keyRepo.findById(keyId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + keyId));

        // Revoke old key
        oldKey.setStatus("ROTATED");
        keyRepo.save(oldKey);

        // Issue new key
        String rawKey = generateApiKey();
        String prefix = rawKey.substring(0, 8);
        String hash = sha256(rawKey);

        ApiKeyEntity newKey = new ApiKeyEntity();
        newKey.setClientId(oldKey.getClientId());
        newKey.setKeyPrefix(prefix);
        newKey.setKeyHash(hash);
        newKey.setLabel(request.label() != null ? request.label() : oldKey.getLabel());
        newKey.setRotatedFromId(keyId);
        if (request.expiresInDays() != null) {
            newKey.setExpiresAt(OffsetDateTime.now().plusDays(request.expiresInDays()));
        }
        newKey = keyRepo.save(newKey);

        emitOutboxEvent("ApiKey", newKey.getId().toString(),
                "impilo.developer-portal.apikey.rotated.v1",
                tenantId, correlationId, idempotencyKey,
                newKey.getId().toString(), "ApiKey",
                Map.of("new_key_id", newKey.getId().toString(),
                        "old_key_id", keyId.toString(),
                        "client_id", oldKey.getClientId().toString()));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("key_id", newKey.getId());
        response.put("api_key", rawKey);
        response.put("key_prefix", prefix);
        response.put("rotated_from", keyId);
        response.put("status", newKey.getStatus());
        return response;
    }

    public List<ApiKeyEntity> listApiKeys(UUID clientId) {
        return keyRepo.findByClientId(clientId);
    }

    @Transactional
    public Map<String, Object> configureSandbox(UUID clientId, UUID tenantId, String correlationId,
                                                  String sandboxConfig) {
        ClientEntity client = clientRepo.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));

        client.setSandboxEnabled(true);
        client.setSandboxConfig(sandboxConfig);
        client.setUpdatedAt(OffsetDateTime.now());
        client = clientRepo.save(client);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("client_id", client.getId());
        response.put("sandbox_enabled", true);
        response.put("sandbox_config", sandboxConfig);
        response.put("updated_at", client.getUpdatedAt().toString());
        return response;
    }

    @Transactional
    public Map<String, Object> updateDeprecationPosture(UUID clientId, String posture) {
        if (!Set.of("NONE", "WARN", "BLOCK").contains(posture)) {
            throw new IllegalArgumentException("Invalid deprecation posture: " + posture
                    + ". Must be one of: NONE, WARN, BLOCK");
        }
        ClientEntity client = clientRepo.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));

        client.setDeprecationPosture(posture);
        client.setUpdatedAt(OffsetDateTime.now());
        client = clientRepo.save(client);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("client_id", client.getId());
        response.put("deprecation_posture", posture);
        response.put("updated_at", client.getUpdatedAt().toString());
        return response;
    }

    @Transactional
    public Map<String, Object> revokeApiKey(UUID keyId) {
        ApiKeyEntity key = keyRepo.findById(keyId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found: " + keyId));
        key.setStatus("REVOKED");
        keyRepo.save(key);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("key_id", key.getId());
        response.put("status", "REVOKED");
        return response;
    }

    private void emitOutboxEvent(String aggregateType, String aggregateId, String eventType,
                                  UUID tenantId, String correlationId, String idempotencyKey,
                                  String subjectId, String subjectType,
                                  Map<String, Object> payload) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setOccurredAt(OffsetDateTime.now());
        if (tenantId != null) event.setTenantId(tenantId);
        if (correlationId != null) {
            try { event.setCorrelationId(UUID.fromString(correlationId)); }
            catch (Exception ignored) {}
        }
        event.setIdempotencyKey(idempotencyKey);
        event.setSubjectId(subjectId);
        event.setSubjectType(subjectType);
        try { event.setPayloadJson(objectMapper.writeValueAsString(payload)); }
        catch (Exception ignored) {}
        outboxRepo.save(event);
    }

    private String generateApiKey() {
        StringBuilder sb = new StringBuilder("imp_");
        for (int i = 0; i < 32; i++) {
            sb.append(KEY_CHARS.charAt(SECURE_RANDOM.nextInt(KEY_CHARS.length())));
        }
        return sb.toString();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) { hex.append(String.format("%02x", b)); }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
