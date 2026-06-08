package zw.gov.mohcc.impilo.iotingestion.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.iotingestion.domain.DeviceRegistryEntity;
import zw.gov.mohcc.impilo.iotingestion.repository.DeviceRegistryRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DeviceRegistryService {

    private final DeviceRegistryRepository repository;

    public DeviceRegistryService(DeviceRegistryRepository repository) {
        this.repository = repository;
    }

    public List<Map<String, Object>> listDevices(UUID tenantId, String ownerHealthId) {
        List<DeviceRegistryEntity> rows = ownerHealthId != null && !ownerHealthId.isBlank()
                ? repository.findByTenantIdAndOwnerHealthIdAndStatusNotOrderByRegisteredAtDesc(tenantId, ownerHealthId, "REVOKED")
                : repository.findByTenantIdAndStatusNotOrderByRegisteredAtDesc(tenantId, "REVOKED");
        return rows.stream().map(this::toView).toList();
    }

    @Transactional
    public Map<String, Object> register(UUID tenantId, String actorId, Map<String, Object> body) {
        String externalId = text(body, "externalDeviceId", "external_device_id", "fingerprint", "deviceName");
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("externalDeviceId is required");
        }
        DeviceRegistryEntity entity = repository.findByTenantIdAndExternalDeviceId(tenantId, externalId)
                .orElseGet(DeviceRegistryEntity::new);
        if (entity.getDeviceId() == null) {
            entity.setDeviceId(UUID.randomUUID());
            entity.setTenantId(tenantId);
            entity.setExternalDeviceId(externalId);
            entity.setRegisteredBy(actorId != null ? actorId : "system");
            entity.setRegisteredAt(OffsetDateTime.now());
        }
        String deviceType = text(body, "deviceType", "device_type", "type");
        entity.setDeviceType(deviceType != null ? deviceType : "MOBILE");
        entity.setManufacturer(text(body, "manufacturer"));
        entity.setModel(text(body, "model", "deviceName", "os"));
        entity.setFirmwareVersion(text(body, "firmwareVersion", "firmware_version"));
        entity.setOwnerHealthId(text(body, "ownerHealthId", "owner_health_id"));
        entity.setTrustLevel("BASIC");
        entity.setStatus("PENDING_VERIFICATION");
        entity.setLastSeenAt(OffsetDateTime.now());
        Object caps = body.get("capabilities");
        if (caps instanceof List<?> list) {
            entity.setCapabilities(list.stream().map(String::valueOf).toList());
        }
        return toView(repository.save(entity));
    }

    @Transactional
    public Map<String, Object> revoke(UUID tenantId, UUID deviceId, String actorId, String reason) {
        DeviceRegistryEntity entity = repository.findByTenantIdAndDeviceId(tenantId, deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found"));
        entity.setStatus("REVOKED");
        entity.setTrustLevel("REVOKED");
        entity.setRevokedAt(OffsetDateTime.now());
        entity.setRevokedBy(actorId);
        entity.setRevocationReason(reason);
        return toView(repository.save(entity));
    }

    public Map<String, Object> trustAssessment(UUID tenantId, UUID deviceId) {
        DeviceRegistryEntity entity = repository.findByTenantIdAndDeviceId(tenantId, deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found"));
        Map<String, Object> trust = new LinkedHashMap<>();
        trust.put("deviceId", entity.getDeviceId().toString());
        trust.put("trustLevel", entity.getTrustLevel());
        trust.put("assessedAt", OffsetDateTime.now().toString());
        trust.put("trustScore", scoreFor(entity.getTrustLevel()));
        trust.put("permittedOperations", List.of("VIEW_RECORDS", "WELLNESS_TRACKING", "MESSAGING"));
        trust.put("restrictedOperations", List.of("PRESCRIBING", "CLAIMS_SUBMISSION"));
        trust.put("upgradeActions", List.of("ENROLL_BIOMETRIC", "ENROLL_MDM"));
        return trust;
    }

    private int scoreFor(String trustLevel) {
        return switch (trustLevel != null ? trustLevel : "UNVERIFIED") {
            case "MEDICAL_GRADE" -> 95;
            case "CERTIFIED" -> 80;
            case "BASIC" -> 55;
            default -> 25;
        };
    }

    private Map<String, Object> toView(DeviceRegistryEntity entity) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("deviceId", entity.getDeviceId().toString());
        view.put("externalDeviceId", entity.getExternalDeviceId());
        view.put("deviceType", entity.getDeviceType());
        view.put("manufacturer", entity.getManufacturer());
        view.put("model", entity.getModel());
        view.put("firmwareVersion", entity.getFirmwareVersion());
        view.put("trustLevel", entity.getTrustLevel());
        view.put("status", entity.getStatus());
        view.put("ownerHealthId", entity.getOwnerHealthId());
        view.put("capabilities", entity.getCapabilities() != null ? entity.getCapabilities() : List.of());
        view.put("registeredAt", entity.getRegisteredAt() != null ? entity.getRegisteredAt().toString() : null);
        view.put("lastSeenAt", entity.getLastSeenAt() != null ? entity.getLastSeenAt().toString() : null);
        return view;
    }

    private static String text(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }
}
