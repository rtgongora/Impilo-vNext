package zw.gov.mohcc.impilo.tshepo.consent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.Consent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.consent.config.ConsentProperties;
import zw.gov.mohcc.impilo.tshepo.consent.dto.ConsentDirectiveDto;
import zw.gov.mohcc.impilo.tshepo.consent.dto.CreateConsentRequest;
import zw.gov.mohcc.impilo.tshepo.consent.dto.UpdateConsentRequest;
import zw.gov.mohcc.impilo.tshepo.consent.exception.ConsentAlreadyRevokedException;
import zw.gov.mohcc.impilo.tshepo.consent.exception.ConsentNotFoundException;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentAuditEntity;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentAuditRepository;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentDirectiveEntity;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.ConsentDirectiveRepository;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.consent.persistence.EventOutboxRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Core CRUD service for consent directives.
 *
 * <p>Every mutation (create, update, revoke) performs within a single transaction:
 * <ol>
 *   <li>Validates the FHIR R4 Consent resource using HAPI FHIR parser</li>
 *   <li>Persists or modifies the consent directive</li>
 *   <li>Creates an immutable audit entry</li>
 *   <li>Writes an outbox event for Kafka publishing</li>
 *   <li>Invalidates the Redis consent cache for the affected patient</li>
 * </ol>
 */
@Service
public class ConsentCrudService {

    private static final Logger log = LoggerFactory.getLogger(ConsentCrudService.class);

    private final ConsentDirectiveRepository consentRepo;
    private final ConsentAuditRepository auditRepo;
    private final EventOutboxRepository outboxRepo;
    private final FhirConsentMapper fhirMapper;
    private final ConsentCacheService cacheService;
    private final ConsentProperties properties;
    private final ObjectMapper objectMapper;

    public ConsentCrudService(ConsentDirectiveRepository consentRepo,
                               ConsentAuditRepository auditRepo,
                               EventOutboxRepository outboxRepo,
                               FhirConsentMapper fhirMapper,
                               ConsentCacheService cacheService,
                               ConsentProperties properties,
                               ObjectMapper objectMapper) {
        this.consentRepo = consentRepo;
        this.auditRepo = auditRepo;
        this.outboxRepo = outboxRepo;
        this.fhirMapper = fhirMapper;
        this.cacheService = cacheService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new consent directive from a FHIR R4 Consent resource.
     *
     * @param request the create request containing the FHIR Consent JSON
     * @param actorId the identity of the actor performing the operation
     * @return the created consent directive as a DTO
     */
    @Transactional
    public ConsentDirectiveDto create(CreateConsentRequest request, String actorId) {
        log.info("Creating consent directive for patient={} by actor={}", request.patientRef(), actorId);

        // Validate the FHIR Consent resource
        Consent fhirConsent = fhirMapper.parseFhirConsent(request.fhirConsentJson());

        // Map to entity
        ConsentDirectiveEntity entity = fhirMapper.toEntity(request, fhirConsent);

        // Apply default TTL if no period end is set
        if (entity.getPeriodEnd() == null) {
            entity.setPeriodEnd(Instant.now().plus(properties.getDefaultTtlDays(), ChronoUnit.DAYS));
        }

        // Persist
        entity = consentRepo.save(entity);

        // Audit
        createAuditEntry(entity, "CREATE", actorId, null);

        // Outbox event
        createOutboxEvent(entity, "ConsentCreated");

        // Invalidate cache
        cacheService.invalidateForPatient(entity.getTenantId(), entity.getPatientRef());

        log.info("Created consent directive id={} for patient={}", entity.getId(), entity.getPatientRef());
        return fhirMapper.toDto(entity);
    }

    /**
     * Retrieves a consent directive by its ID.
     */
    @Transactional(readOnly = true)
    public ConsentDirectiveDto findById(UUID id) {
        ConsentDirectiveEntity entity = consentRepo.findById(id)
                .orElseThrow(() -> new ConsentNotFoundException(id));
        return fhirMapper.toDto(entity);
    }

    /**
     * Retrieves the raw FHIR Consent JSON for a consent directive.
     */
    @Transactional(readOnly = true)
    public String findFhirJsonById(UUID id) {
        ConsentDirectiveEntity entity = consentRepo.findById(id)
                .orElseThrow(() -> new ConsentNotFoundException(id));
        return entity.getFhirConsentJson();
    }

    /**
     * Lists consent directives for a patient, filtered by status.
     */
    @Transactional(readOnly = true)
    public Page<ConsentDirectiveDto> findByPatient(UUID tenantId, String patientRef,
                                                     String status, Pageable pageable) {
        return consentRepo.findByTenantIdAndPatientRefAndStatus(tenantId, patientRef, status, pageable)
                .map(fhirMapper::toDto);
    }

    /**
     * Updates an existing consent directive. Re-validates the FHIR Consent JSON.
     */
    @Transactional
    public ConsentDirectiveDto update(UUID id, UpdateConsentRequest request, String actorId) {
        log.info("Updating consent directive id={} by actor={}", id, actorId);

        ConsentDirectiveEntity entity = consentRepo.findById(id)
                .orElseThrow(() -> new ConsentNotFoundException(id));

        if ("REVOKED".equals(entity.getStatus())) {
            throw new ConsentAlreadyRevokedException(id);
        }

        // Validate updated FHIR Consent
        Consent fhirConsent = fhirMapper.parseFhirConsent(request.fhirConsentJson());

        // Apply updates
        if (request.granteeRef() != null) {
            entity.setGranteeRef(request.granteeRef());
        }
        if (request.scope() != null) {
            entity.setScope(request.scope());
        }
        if (request.purpose() != null) {
            entity.setPurpose(request.purpose());
        }
        if (request.provision() != null) {
            entity.setProvision(request.provision());
        }
        entity.setFhirConsentJson(fhirMapper.serializeFhirConsent(fhirConsent));

        // Extract updated period from FHIR resource
        if (fhirConsent.getProvision() != null && fhirConsent.getProvision().getPeriod() != null) {
            var period = fhirConsent.getProvision().getPeriod();
            if (period.getStart() != null) {
                entity.setPeriodStart(period.getStart().toInstant());
            }
            if (period.getEnd() != null) {
                entity.setPeriodEnd(period.getEnd().toInstant());
            }
        }

        entity = consentRepo.save(entity);

        // Audit
        createAuditEntry(entity, "UPDATE", actorId, Map.of("updatedFields", "fhirConsent,scope,purpose,provision"));

        // Outbox event
        createOutboxEvent(entity, "ConsentUpdated");

        // Invalidate cache
        cacheService.invalidateForPatient(entity.getTenantId(), entity.getPatientRef());

        log.info("Updated consent directive id={}", id);
        return fhirMapper.toDto(entity);
    }

    /**
     * Revokes a consent directive. This is a soft-delete that sets status=REVOKED.
     */
    @Transactional
    public ConsentDirectiveDto revoke(UUID id, String reason, String actorId) {
        log.info("Revoking consent directive id={} by actor={} reason={}", id, actorId, reason);

        ConsentDirectiveEntity entity = consentRepo.findById(id)
                .orElseThrow(() -> new ConsentNotFoundException(id));

        if ("REVOKED".equals(entity.getStatus())) {
            throw new ConsentAlreadyRevokedException(id);
        }

        entity.setStatus("REVOKED");
        entity.setRevokedAt(Instant.now());
        entity.setRevocationReason(reason);

        entity = consentRepo.save(entity);

        // Audit
        createAuditEntry(entity, "REVOKE", actorId, Map.of("reason", reason));

        // Outbox event
        createOutboxEvent(entity, "ConsentRevoked");

        // Invalidate cache
        cacheService.invalidateForPatient(entity.getTenantId(), entity.getPatientRef());

        log.info("Revoked consent directive id={}", id);
        return fhirMapper.toDto(entity);
    }

    // --- Internal helpers ---

    private void createAuditEntry(ConsentDirectiveEntity consent, String action,
                                   String actorId, Object detail) {
        ConsentAuditEntity audit = new ConsentAuditEntity();
        audit.setTenantId(consent.getTenantId());
        audit.setConsentId(consent.getId());
        audit.setAction(action);
        audit.setActorId(actorId);
        if (detail != null) {
            try {
                audit.setDetail(objectMapper.writeValueAsString(detail));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize audit detail for consent={}: {}", consent.getId(), e.getMessage());
                audit.setDetail("{}");
            }
        }
        auditRepo.save(audit);
    }

    private void createOutboxEvent(ConsentDirectiveEntity consent, String eventType) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("ConsentDirective");
        outbox.setAggregateId(consent.getId().toString());
        outbox.setEventType(eventType);
        try {
            Map<String, Object> payload = Map.of(
                    "consentId", consent.getId().toString(),
                    "tenantId", consent.getTenantId().toString(),
                    "patientRef", consent.getPatientRef(),
                    "status", consent.getStatus(),
                    "scope", consent.getScope(),
                    "purpose", consent.getPurpose(),
                    "provision", consent.getProvision(),
                    "timestamp", Instant.now().toString()
            );
            outbox.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox event for consent={}: {}", consent.getId(), e.getMessage());
            outbox.setPayload("{}");
        }
        outboxRepo.save(outbox);
    }
}
