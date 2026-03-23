package zw.gov.mohcc.impilo.devportal.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.devportal.api.dto.IssueKeyRequest;
import zw.gov.mohcc.impilo.devportal.api.dto.RegisterClientRequest;
import zw.gov.mohcc.impilo.devportal.api.dto.RotateKeyRequest;
import zw.gov.mohcc.impilo.devportal.domain.ApiKeyEntity;
import zw.gov.mohcc.impilo.devportal.domain.CertificationEntity;
import zw.gov.mohcc.impilo.devportal.domain.ClientEntity;
import zw.gov.mohcc.impilo.devportal.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.devportal.repository.ApiKeyRepository;
import zw.gov.mohcc.impilo.devportal.repository.CertificationRepository;
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
    private final CertificationRepository certRepo;
    private final OutboxEventRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public DeveloperPortalService(ClientRepository clientRepo, ApiKeyRepository keyRepo,
                                   CertificationRepository certRepo,
                                   OutboxEventRepository outboxRepo, ObjectMapper objectMapper) {
        this.clientRepo = clientRepo;
        this.keyRepo = keyRepo;
        this.certRepo = certRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    public DeveloperPortalService(ClientRepository clientRepo, ApiKeyRepository keyRepo,
                                   OutboxEventRepository outboxRepo, ObjectMapper objectMapper) {
        this(clientRepo, keyRepo, null, outboxRepo, objectMapper);
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

    // ── Certification ──

    @Transactional
    public Map<String, Object> runCertification(UUID clientId, UUID tenantId, String correlationId,
                                                  String idempotencyKey, String triggeredBy) {
        ClientEntity client = clientRepo.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));

        List<Map<String, Object>> checks = new ArrayList<>();
        int passed = 0;
        int failed = 0;

        // Check 1: Client has at least one active API key
        List<ApiKeyEntity> activeKeys = keyRepo.findByClientId(clientId).stream()
                .filter(k -> "ACTIVE".equals(k.getStatus())).toList();
        boolean hasActiveKey = !activeKeys.isEmpty();
        checks.add(Map.of("check", "active_api_key", "passed", hasActiveKey,
                "detail", hasActiveKey ? "Client has " + activeKeys.size() + " active key(s)" : "No active API keys found"));
        if (hasActiveKey) passed++; else failed++;

        // Check 2: Contact email provided
        boolean hasEmail = client.getContactEmail() != null && !client.getContactEmail().isBlank();
        checks.add(Map.of("check", "contact_email", "passed", hasEmail,
                "detail", hasEmail ? "Contact email configured" : "Contact email missing"));
        if (hasEmail) passed++; else failed++;

        // Check 3: Client is active
        boolean isActive = "ACTIVE".equals(client.getStatus());
        checks.add(Map.of("check", "client_active", "passed", isActive,
                "detail", isActive ? "Client status is ACTIVE" : "Client status is " + client.getStatus()));
        if (isActive) passed++; else failed++;

        // Check 4: Deprecation posture set (not NONE)
        boolean hasDeprecationPosture = !"NONE".equals(client.getDeprecationPosture());
        checks.add(Map.of("check", "deprecation_posture_configured", "passed", hasDeprecationPosture,
                "detail", hasDeprecationPosture ? "Deprecation posture: " + client.getDeprecationPosture() : "Deprecation posture not configured (NONE)"));
        if (hasDeprecationPosture) passed++; else failed++;

        // Check 5: Sandbox tested (sandbox was enabled at some point)
        boolean sandboxTested = client.isSandboxEnabled() || client.getSandboxConfig() != null;
        checks.add(Map.of("check", "sandbox_tested", "passed", sandboxTested,
                "detail", sandboxTested ? "Sandbox environment used" : "Sandbox never activated"));
        if (sandboxTested) passed++; else failed++;

        // Check 6: API key has scopes defined
        boolean hasScopedKey = activeKeys.stream().anyMatch(k -> k.getLabel() != null && !k.getLabel().isBlank());
        checks.add(Map.of("check", "key_labelled", "passed", hasScopedKey,
                "detail", hasScopedKey ? "API keys have labels" : "API keys missing labels"));
        if (hasScopedKey) passed++; else failed++;

        // Check 7: Key age < 90 days
        boolean keyFresh = activeKeys.stream().allMatch(k ->
                k.getCreatedAt().isAfter(OffsetDateTime.now().minusDays(90)));
        checks.add(Map.of("check", "key_freshness", "passed", keyFresh,
                "detail", keyFresh ? "All keys issued within 90 days" : "Some keys older than 90 days"));
        if (keyFresh) passed++; else failed++;

        int total = checks.size();
        String result = failed == 0 ? "PASS" : "FAIL";

        CertificationEntity cert = new CertificationEntity();
        cert.setClientId(clientId);
        cert.setTenantId(tenantId);
        cert.setStatus("COMPLETED");
        cert.setResult(result);
        cert.setChecksTotal(total);
        cert.setChecksPassed(passed);
        cert.setChecksFailed(failed);
        cert.setTriggeredBy(triggeredBy);
        cert.setCompletedAt(OffsetDateTime.now());
        try {
            cert.setReportJson(objectMapper.writeValueAsString(Map.of("checks", checks)));
        } catch (Exception ignored) {}
        cert = certRepo.save(cert);

        emitOutboxEvent("Certification", cert.getId().toString(),
                "impilo.developer-portal.certification.completed.v1",
                tenantId, correlationId, idempotencyKey,
                cert.getId().toString(), "Certification",
                Map.of("certification_id", cert.getId().toString(),
                        "client_id", clientId.toString(),
                        "result", result,
                        "passed", passed, "failed", failed, "total", total));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("certification_id", cert.getId());
        response.put("client_id", clientId);
        response.put("status", cert.getStatus());
        response.put("result", result);
        response.put("checks_total", total);
        response.put("checks_passed", passed);
        response.put("checks_failed", failed);
        response.put("checks", checks);
        response.put("started_at", cert.getStartedAt().toString());
        response.put("completed_at", cert.getCompletedAt().toString());
        return response;
    }

    public List<CertificationEntity> listCertifications(UUID clientId) {
        return certRepo.findByClientIdOrderByCreatedAtDesc(clientId);
    }

    public Optional<CertificationEntity> getCertification(UUID certId) {
        return certRepo.findById(certId);
    }

    // ── Federation Readiness ──

    public Map<String, Object> checkFederationReadiness(UUID clientId) {
        ClientEntity client = clientRepo.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + clientId));

        List<ApiKeyEntity> activeKeys = keyRepo.findByClientId(clientId).stream()
                .filter(k -> "ACTIVE".equals(k.getStatus())).toList();
        List<CertificationEntity> certs = certRepo.findByClientIdOrderByCreatedAtDesc(clientId);
        boolean hasCertPass = certs.stream().anyMatch(c -> "PASS".equals(c.getResult()));

        List<Map<String, Object>> items = new ArrayList<>();
        boolean readyAll = true;

        // Item 1: Client registered
        items.add(Map.of("item", "client_registered", "ready", true, "detail", "Client registered: " + client.getClientName()));

        // Item 2: Active API key
        boolean hasKey = !activeKeys.isEmpty();
        items.add(Map.of("item", "api_key_active", "ready", hasKey, "detail", hasKey ? activeKeys.size() + " active key(s)" : "No active keys"));
        readyAll = readyAll && hasKey;

        // Item 3: Certification passed
        items.add(Map.of("item", "certification_passed", "ready", hasCertPass, "detail", hasCertPass ? "Last pass: " + certs.stream().filter(c -> "PASS".equals(c.getResult())).findFirst().map(c -> c.getCompletedAt().toString()).orElse("unknown") : "No certification passed"));
        readyAll = readyAll && hasCertPass;

        // Item 4: Sandbox tested
        boolean sandboxReady = client.isSandboxEnabled();
        items.add(Map.of("item", "sandbox_tested", "ready", sandboxReady, "detail", sandboxReady ? "Sandbox enabled" : "Sandbox not enabled"));
        readyAll = readyAll && sandboxReady;

        // Item 5: Deprecation posture
        boolean postureSet = !"NONE".equals(client.getDeprecationPosture());
        items.add(Map.of("item", "deprecation_posture_set", "ready", postureSet, "detail", "Posture: " + client.getDeprecationPosture()));
        readyAll = readyAll && postureSet;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("client_id", clientId);
        response.put("client_name", client.getClientName());
        response.put("overall_ready", readyAll);
        response.put("checklist", items);
        response.put("checked_at", OffsetDateTime.now().toString());
        return response;
    }

    // ── Dashboard Stats ──

    public Map<String, Object> getDashboardStats(UUID tenantId) {
        List<ClientEntity> clients = clientRepo.findByTenantId(tenantId);
        long activeClients = clients.stream().filter(c -> "ACTIVE".equals(c.getStatus())).count();
        long sandboxClients = clients.stream().filter(ClientEntity::isSandboxEnabled).count();

        List<ApiKeyEntity> allKeys = new ArrayList<>();
        for (ClientEntity c : clients) {
            allKeys.addAll(keyRepo.findByClientId(c.getId()));
        }
        long activeKeys = allKeys.stream().filter(k -> "ACTIVE".equals(k.getStatus())).count();
        long revokedKeys = allKeys.stream().filter(k -> "REVOKED".equals(k.getStatus())).count();
        long rotatedKeys = allKeys.stream().filter(k -> "ROTATED".equals(k.getStatus())).count();

        List<CertificationEntity> allCerts = certRepo.findByTenantIdOrderByCreatedAtDesc(tenantId);
        long certsPassed = allCerts.stream().filter(c -> "PASS".equals(c.getResult())).count();
        long certsFailed = allCerts.stream().filter(c -> "FAIL".equals(c.getResult())).count();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("total_clients", clients.size());
        response.put("active_clients", activeClients);
        response.put("sandbox_clients", sandboxClients);
        response.put("total_keys", allKeys.size());
        response.put("active_keys", activeKeys);
        response.put("revoked_keys", revokedKeys);
        response.put("rotated_keys", rotatedKeys);
        response.put("total_certifications", allCerts.size());
        response.put("certifications_passed", certsPassed);
        response.put("certifications_failed", certsFailed);
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
