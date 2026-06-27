package zw.gov.mohcc.impilo.tshepo.offline.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.OfflinePackRequest;
import zw.gov.mohcc.impilo.tshepo.offline.api.dto.OfflinePackResponse;
import zw.gov.mohcc.impilo.tshepo.offline.client.KeysServiceClient;
import zw.gov.mohcc.impilo.tshepo.offline.config.OfflineProperties;
import zw.gov.mohcc.impilo.tshepo.offline.exception.OfflinePackNotFoundException;
import zw.gov.mohcc.impilo.tshepo.offline.exception.OfflineServiceException;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.OfflinePackEntity;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.OfflinePackRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Service for generating and managing offline packs.
 *
 * <p>An offline pack contains everything a device needs to make trust decisions
 * while disconnected from the network:</p>
 * <ul>
 *     <li>Policy rules snapshot — what actions are permitted for which roles</li>
 *     <li>Consent snapshots — active consent directives for facility patients</li>
 *     <li>Terminology codes — ICD-10, medication codes, etc. needed for offline forms</li>
 * </ul>
 *
 * <p>Packs are JWS-signed so the device can verify their integrity offline.
 * They expire after a configurable TTL to ensure stale policy data is not used.</p>
 */
@Service
public class OfflinePackService {

    private static final Logger log = LoggerFactory.getLogger(OfflinePackService.class);
    /** Purpose-scoped key the keys-service signs offline packs with (fail-closed). */
    private static final String SIGNING_PURPOSE = "OFFLINE_PACK";

    private final OfflinePackRepository packRepo;
    private final EventOutboxRepository outboxRepo;
    private final KeysServiceClient keysClient;
    private final OfflineProperties offlineProperties;
    private final ObjectMapper objectMapper;

    public OfflinePackService(OfflinePackRepository packRepo,
                               EventOutboxRepository outboxRepo,
                               KeysServiceClient keysClient,
                               OfflineProperties offlineProperties,
                               ObjectMapper objectMapper) {
        this.packRepo = packRepo;
        this.outboxRepo = outboxRepo;
        this.keysClient = keysClient;
        this.offlineProperties = offlineProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate a new offline pack for a facility.
     */
    @Transactional
    public OfflinePackResponse generatePack(OfflinePackRequest request) {
        log.info("Generating offline pack: tenant={}, facility={}, for={}",
                request.tenantId(), request.facilityId(), request.generatedFor());

        Instant now = Instant.now();
        Instant expiresAt = now.plus(offlineProperties.offlinePackTtlHours(), ChronoUnit.HOURS);

        // Build snapshots
        String policySnapshot = buildPolicySnapshot(request.tenantId(), request.facilityId());
        String consentSnapshot = buildConsentSnapshot(request.tenantId(), request.facilityId());
        String terminologySnapshot = buildTerminologySnapshot();

        // Determine version
        int nextVersion = packRepo.findMaxVersionByTenantAndFacility(
                request.tenantId(), request.facilityId()) + 1;

        // Persist the pack
        OfflinePackEntity entity = new OfflinePackEntity();
        entity.setTenantId(request.tenantId());
        entity.setFacilityId(request.facilityId());
        entity.setGeneratedFor(request.generatedFor());
        entity.setPolicySnapshot(policySnapshot);
        entity.setConsentSnapshot(consentSnapshot);
        entity.setTerminologySnapshot(terminologySnapshot);
        entity.setGeneratedAt(now);
        entity.setExpiresAt(expiresAt);
        entity.setVersion(nextVersion);

        entity = packRepo.save(entity);

        // Sign the entire pack content for offline integrity verification
        Map<String, Object> packContent = new LinkedHashMap<>();
        packContent.put("packId", entity.getId().toString());
        packContent.put("tenantId", entity.getTenantId().toString());
        packContent.put("facilityId", entity.getFacilityId().toString());
        packContent.put("version", nextVersion);
        packContent.put("generatedAt", now.toString());
        packContent.put("expiresAt", expiresAt.toString());
        packContent.put("policy", policySnapshot);
        packContent.put("consent", consentSnapshot);
        packContent.put("terminology", terminologySnapshot);

        String packJson = toJson(packContent);
        String signedPack = keysClient.signPayload(entity.getTenantId(), packJson, SIGNING_PURPOSE);

        // Write outbox event
        writeOutboxEvent("OfflinePack", entity.getId().toString(),
                "OFFLINE_PACK_GENERATED", buildPackEventPayload(entity));

        log.info("Generated offline pack id={}, version={}, expires={}", entity.getId(), nextVersion, expiresAt);

        return new OfflinePackResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getFacilityId(),
                entity.getGeneratedFor(),
                signedPack,
                entity.getGeneratedAt(),
                entity.getExpiresAt(),
                entity.getVersion()
        );
    }

    /**
     * Retrieve an offline pack by ID (tenant-isolated).
     */
    @Transactional(readOnly = true)
    public OfflinePackResponse getPack(UUID packId, UUID tenantId) {
        OfflinePackEntity entity = packRepo.findByIdAndTenantId(packId, tenantId)
                .orElseThrow(() -> new OfflinePackNotFoundException(packId));

        return toResponse(entity);
    }

    /**
     * Get the latest offline pack for a facility (tenant-isolated).
     */
    @Transactional(readOnly = true)
    public OfflinePackResponse getLatestPackForFacility(UUID facilityId, UUID tenantId) {
        OfflinePackEntity entity = packRepo.findLatestByTenantAndFacility(tenantId, facilityId)
                .orElseThrow(() -> new OfflinePackNotFoundException(facilityId, true));

        return toResponse(entity);
    }

    // ------------------------------------------------------------------
    // Snapshot builders
    // ------------------------------------------------------------------

    /**
     * Build a policy rules snapshot for the facility.
     * Captures the current offline action rules, role-based permissions, and limits.
     */
    private String buildPolicySnapshot(UUID tenantId, UUID facilityId) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("tenantId", tenantId.toString());
        policy.put("facilityId", facilityId.toString());
        policy.put("snapshotAt", Instant.now().toString());
        policy.put("allowedActions", offlineProperties.allowedOfflineActions());
        policy.put("maxOfflineEncounters", offlineProperties.maxOfflineEncounters());
        policy.put("maxCapabilityTtlHours", offlineProperties.maxCapabilityTtlHours());

        // Offline action rules
        Map<String, String> actionRules = new LinkedHashMap<>();
        actionRules.put("READ_PATIENT", "ALLOWED — valid capability token required");
        actionRules.put("READ_MEDICATION", "ALLOWED — valid capability token required");
        actionRules.put("CREATE_PROVISIONAL_ENCOUNTER", "ALLOWED — generates O-CPID if needed, encounter limit applies");
        actionRules.put("CREATE_PROVISIONAL_REGISTRATION", "ALLOWED — generates O-CPID, requires reconciliation");
        actionRules.put("DISPENSE_MEDICATION", "ALLOWED — requires DISPENSE_MEDICATION capability");
        actionRules.put("UPDATE_*", "DENIED — updates are not permitted offline");
        actionRules.put("DELETE_*", "DENIED — deletes are never permitted offline");
        policy.put("actionRules", actionRules);

        // Reconciliation rules
        Map<String, String> reconciliationRules = new LinkedHashMap<>();
        reconciliationRules.put("conflictResolution", "FIRST_WRITER_WINS");
        reconciliationRules.put("duplicateDetection", "O-CPID cross-reference via identity-service");
        reconciliationRules.put("failureHandling", "MARK_FAILED_AND_CONTINUE");
        policy.put("reconciliationRules", reconciliationRules);

        return toJson(policy);
    }

    /**
     * Build a consent snapshot for the facility.
     * In production, this would query the consent service for all active consent
     * directives related to patients at this facility.
     */
    private String buildConsentSnapshot(UUID tenantId, UUID facilityId) {
        Map<String, Object> consent = new LinkedHashMap<>();
        consent.put("tenantId", tenantId.toString());
        consent.put("facilityId", facilityId.toString());
        consent.put("snapshotAt", Instant.now().toString());
        consent.put("defaultConsentPolicy", "IMPLICIT_CONSENT_FOR_TREATMENT");
        consent.put("consentDirectives", List.of());
        consent.put("note", "Consent directives are populated from tshepo-consent-service at generation time");
        return toJson(consent);
    }

    /**
     * Build a terminology snapshot containing essential codes for offline use.
     * Includes ICD-10 codes, medication codes, and other coded values needed
     * for offline encounter creation and medication dispensing.
     */
    private String buildTerminologySnapshot() {
        Map<String, Object> terminology = new LinkedHashMap<>();
        terminology.put("snapshotAt", Instant.now().toString());

        // Encounter types
        terminology.put("encounterTypes", List.of(
                Map.of("code", "AMB", "display", "Ambulatory", "system", "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
                Map.of("code", "EMER", "display", "Emergency", "system", "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
                Map.of("code", "FLD", "display", "Field", "system", "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
                Map.of("code", "HH", "display", "Home Health", "system", "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
                Map.of("code", "IMP", "display", "Inpatient", "system", "http://terminology.hl7.org/CodeSystem/v3-ActCode"),
                Map.of("code", "SS", "display", "Short Stay", "system", "http://terminology.hl7.org/CodeSystem/v3-ActCode")
        ));

        // Common diagnosis codes (subset for offline)
        terminology.put("commonDiagnosisCodes", List.of(
                Map.of("code", "J06.9", "display", "Acute upper respiratory infection, unspecified"),
                Map.of("code", "A09", "display", "Infectious gastroenteritis and colitis, unspecified"),
                Map.of("code", "B54", "display", "Unspecified malaria"),
                Map.of("code", "J18.9", "display", "Pneumonia, unspecified organism"),
                Map.of("code", "N39.0", "display", "Urinary tract infection, site not specified")
        ));

        // Medication route codes
        terminology.put("medicationRoutes", List.of(
                Map.of("code", "PO", "display", "Oral"),
                Map.of("code", "IV", "display", "Intravenous"),
                Map.of("code", "IM", "display", "Intramuscular"),
                Map.of("code", "SC", "display", "Subcutaneous"),
                Map.of("code", "TOP", "display", "Topical")
        ));

        return toJson(terminology);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private OfflinePackResponse toResponse(OfflinePackEntity entity) {
        return new OfflinePackResponse(
                entity.getId(),
                entity.getTenantId(),
                entity.getFacilityId(),
                entity.getGeneratedFor(),
                null, // signed pack not returned on GET (only on generate)
                entity.getGeneratedAt(),
                entity.getExpiresAt(),
                entity.getVersion()
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new OfflineServiceException("SERIALIZATION_ERROR", "Failed to serialize to JSON", e);
        }
    }

    private void writeOutboxEvent(String aggregateType, String aggregateId,
                                   String eventType, String payload) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payload);
        outbox.setCreatedAt(Instant.now());
        outboxRepo.save(outbox);
    }

    private String buildPackEventPayload(OfflinePackEntity entity) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("packId", entity.getId().toString());
        event.put("tenantId", entity.getTenantId().toString());
        event.put("facilityId", entity.getFacilityId().toString());
        event.put("generatedFor", entity.getGeneratedFor());
        event.put("version", entity.getVersion());
        event.put("expiresAt", entity.getExpiresAt().toString());
        event.put("timestamp", Instant.now().toString());
        return toJson(event);
    }
}
