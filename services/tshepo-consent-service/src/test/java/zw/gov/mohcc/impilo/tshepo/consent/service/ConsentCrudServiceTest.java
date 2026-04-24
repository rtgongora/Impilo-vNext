package zw.gov.mohcc.impilo.tshepo.consent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.Consent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.tshepo.consent.config.ConsentProperties;
import zw.gov.mohcc.impilo.tshepo.consent.dto.ConsentDirectiveDto;
import zw.gov.mohcc.impilo.tshepo.consent.dto.CreateConsentRequest;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConsentCrudService}.
 *
 * <p>Tests the full lifecycle of consent directives: create, find, update, revoke.
 * Verifies that every mutation creates an audit entry, publishes an outbox event,
 * and invalidates the Redis cache for the affected patient.</p>
 */
@ExtendWith(MockitoExtension.class)
class ConsentCrudServiceTest {

    @Mock
    private ConsentDirectiveRepository consentRepo;

    @Mock
    private ConsentAuditRepository auditRepo;

    @Mock
    private EventOutboxRepository outboxRepo;

    @Mock
    private FhirConsentMapper fhirMapper;

    @Mock
    private ConsentCacheService cacheService;

    @Mock
    private ConsentProperties properties;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ConsentCrudService crudService;

    @Captor
    private ArgumentCaptor<ConsentAuditEntity> auditCaptor;

    @Captor
    private ArgumentCaptor<EventOutboxEntity> outboxCaptor;

    @Captor
    private ArgumentCaptor<ConsentDirectiveEntity> entityCaptor;

    // --- Shared test fixtures ---
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PATIENT_REF = "Patient/CPID-456";
    private static final String GRANTOR_REF = "Patient/CPID-456";
    private static final String GRANTEE_REF = "Practitioner/prac-123";
    private static final String ACTOR_ID = "practitioner-123";
    private static final String SCOPE = "clinical-data";
    private static final String PURPOSE = "TREATMENT";
    private static final String FHIR_JSON = "{\"resourceType\":\"Consent\",\"status\":\"active\"}";

    /**
     * Builds a persisted consent directive entity with an ID set.
     */
    private ConsentDirectiveEntity buildPersistedEntity() {
        ConsentDirectiveEntity entity = new ConsentDirectiveEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(TENANT_ID);
        entity.setPatientRef(PATIENT_REF);
        entity.setGrantorRef(GRANTOR_REF);
        entity.setGranteeRef(GRANTEE_REF);
        entity.setStatus("ACTIVE");
        entity.setScope(SCOPE);
        entity.setPurpose(PURPOSE);
        entity.setProvision("permit");
        entity.setFhirConsentJson(FHIR_JSON);
        entity.setPeriodStart(Instant.now().minus(1, ChronoUnit.DAYS));
        entity.setPeriodEnd(Instant.now().plus(364, ChronoUnit.DAYS));
        entity.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        return entity;
    }

    /**
     * Builds a standard CreateConsentRequest for testing.
     */
    private CreateConsentRequest buildCreateRequest() {
        return new CreateConsentRequest(
                TENANT_ID,
                PATIENT_REF,
                GRANTOR_REF,
                GRANTEE_REF,
                SCOPE,
                PURPOSE,
                "permit",
                FHIR_JSON
        );
    }

    /**
     * Builds a ConsentDirectiveDto for mapper stub returns.
     */
    private ConsentDirectiveDto buildDto(ConsentDirectiveEntity entity) {
        return new ConsentDirectiveDto(
                entity.getId(),
                entity.getTenantId(),
                entity.getPatientRef(),
                entity.getGrantorRef(),
                entity.getGranteeRef(),
                entity.getStatus(),
                entity.getScope(),
                entity.getPurpose(),
                entity.getProvision(),
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getRevokedAt(),
                entity.getRevocationReason()
        );
    }

    // =========================================================================
    // Create tests
    // =========================================================================

    @Nested
    @DisplayName("Create consent directive")
    class CreateTests {

        @Test
        @DisplayName("create_savesConsentDirective - persists entity and returns DTO")
        void create_savesConsentDirective() {
            CreateConsentRequest request = buildCreateRequest();
            Consent fhirConsent = new Consent();
            ConsentDirectiveEntity newEntity = buildPersistedEntity();
            ConsentDirectiveDto expectedDto = buildDto(newEntity);

            when(fhirMapper.parseFhirConsent(FHIR_JSON)).thenReturn(fhirConsent);
            when(fhirMapper.toEntity(request, fhirConsent)).thenReturn(newEntity);
            when(consentRepo.save(any(ConsentDirectiveEntity.class))).thenReturn(newEntity);
            when(fhirMapper.toDto(newEntity)).thenReturn(expectedDto);

            ConsentDirectiveDto result = crudService.create(request, ACTOR_ID);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(newEntity.getId());
            assertThat(result.tenantId()).isEqualTo(TENANT_ID);
            assertThat(result.patientRef()).isEqualTo(PATIENT_REF);
            assertThat(result.status()).isEqualTo("ACTIVE");
            assertThat(result.scope()).isEqualTo(SCOPE);
            assertThat(result.purpose()).isEqualTo(PURPOSE);
            assertThat(result.provision()).isEqualTo("permit");

            // Verify entity was saved
            verify(consentRepo).save(any(ConsentDirectiveEntity.class));

            // Verify FHIR JSON was validated
            verify(fhirMapper).parseFhirConsent(FHIR_JSON);
        }

        @Test
        @DisplayName("create_publishesEvent - creates outbox event with ConsentCreated type")
        void create_publishesEvent() {
            CreateConsentRequest request = buildCreateRequest();
            Consent fhirConsent = new Consent();
            ConsentDirectiveEntity newEntity = buildPersistedEntity();
            ConsentDirectiveDto expectedDto = buildDto(newEntity);

            when(fhirMapper.parseFhirConsent(FHIR_JSON)).thenReturn(fhirConsent);
            when(fhirMapper.toEntity(request, fhirConsent)).thenReturn(newEntity);
            when(consentRepo.save(any(ConsentDirectiveEntity.class))).thenReturn(newEntity);
            when(fhirMapper.toDto(newEntity)).thenReturn(expectedDto);

            crudService.create(request, ACTOR_ID);

            // Verify outbox event was created
            verify(outboxRepo).save(outboxCaptor.capture());
            EventOutboxEntity outboxEvent = outboxCaptor.getValue();
            assertThat(outboxEvent.getAggregateType()).isEqualTo("ConsentDirective");
            assertThat(outboxEvent.getAggregateId()).isEqualTo(newEntity.getId().toString());
            assertThat(outboxEvent.getEventType()).isEqualTo("ConsentCreated");
            assertThat(outboxEvent.getPayload()).isNotEmpty();
        }

        @Test
        @DisplayName("create_createsAuditEntry - writes CREATE audit record")
        void create_createsAuditEntry() {
            CreateConsentRequest request = buildCreateRequest();
            Consent fhirConsent = new Consent();
            ConsentDirectiveEntity newEntity = buildPersistedEntity();
            ConsentDirectiveDto expectedDto = buildDto(newEntity);

            when(fhirMapper.parseFhirConsent(FHIR_JSON)).thenReturn(fhirConsent);
            when(fhirMapper.toEntity(request, fhirConsent)).thenReturn(newEntity);
            when(consentRepo.save(any(ConsentDirectiveEntity.class))).thenReturn(newEntity);
            when(fhirMapper.toDto(newEntity)).thenReturn(expectedDto);

            crudService.create(request, ACTOR_ID);

            // Verify audit entry was created
            verify(auditRepo).save(auditCaptor.capture());
            ConsentAuditEntity audit = auditCaptor.getValue();
            assertThat(audit.getTenantId()).isEqualTo(TENANT_ID);
            assertThat(audit.getConsentId()).isEqualTo(newEntity.getId());
            assertThat(audit.getAction()).isEqualTo("CREATE");
            assertThat(audit.getActorId()).isEqualTo(ACTOR_ID);
        }

        @Test
        @DisplayName("create_invalidatesCache - invalidates Redis cache for patient after create")
        void create_invalidatesCache() {
            CreateConsentRequest request = buildCreateRequest();
            Consent fhirConsent = new Consent();
            ConsentDirectiveEntity newEntity = buildPersistedEntity();
            ConsentDirectiveDto expectedDto = buildDto(newEntity);

            when(fhirMapper.parseFhirConsent(FHIR_JSON)).thenReturn(fhirConsent);
            when(fhirMapper.toEntity(request, fhirConsent)).thenReturn(newEntity);
            when(consentRepo.save(any(ConsentDirectiveEntity.class))).thenReturn(newEntity);
            when(fhirMapper.toDto(newEntity)).thenReturn(expectedDto);

            crudService.create(request, ACTOR_ID);

            verify(cacheService).invalidateForPatient(TENANT_ID, PATIENT_REF);
        }

        @Test
        @DisplayName("create_appliesDefaultTtl_whenNoPeriodEnd - sets default period end from config")
        void create_appliesDefaultTtl_whenNoPeriodEnd() {
            CreateConsentRequest request = buildCreateRequest();
            Consent fhirConsent = new Consent();
            ConsentDirectiveEntity newEntity = buildPersistedEntity();
            newEntity.setPeriodEnd(null); // No period end set
            ConsentDirectiveDto expectedDto = buildDto(newEntity);

            when(fhirMapper.parseFhirConsent(FHIR_JSON)).thenReturn(fhirConsent);
            when(fhirMapper.toEntity(request, fhirConsent)).thenReturn(newEntity);
            when(consentRepo.save(any(ConsentDirectiveEntity.class))).thenAnswer(invocation -> {
                ConsentDirectiveEntity saved = invocation.getArgument(0);
                // After the service applies default TTL, periodEnd should be set
                assertThat(saved.getPeriodEnd()).isNotNull();
                return saved;
            });
            when(fhirMapper.toDto(any(ConsentDirectiveEntity.class))).thenReturn(expectedDto);
            when(properties.getDefaultTtlDays()).thenReturn(365);

            crudService.create(request, ACTOR_ID);

            verify(consentRepo).save(entityCaptor.capture());
            ConsentDirectiveEntity captured = entityCaptor.getValue();
            assertThat(captured.getPeriodEnd()).isNotNull();
            // The period end should be approximately 365 days from now
            assertThat(captured.getPeriodEnd()).isAfter(Instant.now().plus(364, ChronoUnit.DAYS));
        }
    }

    // =========================================================================
    // Revoke tests
    // =========================================================================

    @Nested
    @DisplayName("Revoke consent directive")
    class RevokeTests {

        @Test
        @DisplayName("revoke_setsStatusInactive - changes status to REVOKED with reason and timestamp")
        void revoke_setsStatusInactive() {
            ConsentDirectiveEntity entity = buildPersistedEntity();
            UUID consentId = entity.getId();
            String reason = "Patient requested withdrawal";

            when(consentRepo.findById(consentId)).thenReturn(Optional.of(entity));
            when(consentRepo.save(any(ConsentDirectiveEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fhirMapper.toDto(any(ConsentDirectiveEntity.class))).thenAnswer(invocation -> {
                ConsentDirectiveEntity e = invocation.getArgument(0);
                return buildDto(e);
            });

            ConsentDirectiveDto result = crudService.revoke(consentId, reason, ACTOR_ID);

            verify(consentRepo).save(entityCaptor.capture());
            ConsentDirectiveEntity saved = entityCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo("REVOKED");
            assertThat(saved.getRevokedAt()).isNotNull();
            assertThat(saved.getRevocationReason()).isEqualTo(reason);
        }

        @Test
        @DisplayName("revoke_publishesConsentRevokedEvent - creates outbox event")
        void revoke_publishesConsentRevokedEvent() {
            ConsentDirectiveEntity entity = buildPersistedEntity();
            UUID consentId = entity.getId();

            when(consentRepo.findById(consentId)).thenReturn(Optional.of(entity));
            when(consentRepo.save(any(ConsentDirectiveEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fhirMapper.toDto(any(ConsentDirectiveEntity.class))).thenAnswer(invocation -> buildDto(invocation.getArgument(0)));

            crudService.revoke(consentId, "revocation reason", ACTOR_ID);

            verify(outboxRepo).save(outboxCaptor.capture());
            EventOutboxEntity outbox = outboxCaptor.getValue();
            assertThat(outbox.getEventType()).isEqualTo("ConsentRevoked");
            assertThat(outbox.getAggregateType()).isEqualTo("ConsentDirective");
            assertThat(outbox.getAggregateId()).isEqualTo(consentId.toString());
        }

        @Test
        @DisplayName("revoke_createsAuditEntry - writes REVOKE audit record with reason")
        void revoke_createsAuditEntry() {
            ConsentDirectiveEntity entity = buildPersistedEntity();
            UUID consentId = entity.getId();
            String reason = "Patient withdrawal";

            when(consentRepo.findById(consentId)).thenReturn(Optional.of(entity));
            when(consentRepo.save(any(ConsentDirectiveEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fhirMapper.toDto(any(ConsentDirectiveEntity.class))).thenAnswer(invocation -> buildDto(invocation.getArgument(0)));

            crudService.revoke(consentId, reason, ACTOR_ID);

            verify(auditRepo).save(auditCaptor.capture());
            ConsentAuditEntity audit = auditCaptor.getValue();
            assertThat(audit.getAction()).isEqualTo("REVOKE");
            assertThat(audit.getActorId()).isEqualTo(ACTOR_ID);
            assertThat(audit.getConsentId()).isEqualTo(consentId);
            assertThat(audit.getDetail()).contains("reason");
        }

        @Test
        @DisplayName("revoke_invalidatesCache - invalidates Redis cache for patient")
        void revoke_invalidatesCache() {
            ConsentDirectiveEntity entity = buildPersistedEntity();
            UUID consentId = entity.getId();

            when(consentRepo.findById(consentId)).thenReturn(Optional.of(entity));
            when(consentRepo.save(any(ConsentDirectiveEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(fhirMapper.toDto(any(ConsentDirectiveEntity.class))).thenAnswer(invocation -> buildDto(invocation.getArgument(0)));

            crudService.revoke(consentId, "reason", ACTOR_ID);

            verify(cacheService).invalidateForPatient(TENANT_ID, PATIENT_REF);
        }

        @Test
        @DisplayName("revoke_alreadyRevoked_throws - throws ConsentAlreadyRevokedException for revoked consent")
        void revoke_alreadyRevoked_throws() {
            ConsentDirectiveEntity entity = buildPersistedEntity();
            entity.setStatus("REVOKED");
            UUID consentId = entity.getId();

            when(consentRepo.findById(consentId)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> crudService.revoke(consentId, "reason", ACTOR_ID))
                    .isInstanceOf(ConsentAlreadyRevokedException.class);

            // Verify no save was attempted
            verify(consentRepo, never()).save(any());
            verify(outboxRepo, never()).save(any());
            verify(auditRepo, never()).save(any());
        }

        @Test
        @DisplayName("revoke_notFound_throws - throws ConsentNotFoundException for missing consent")
        void revoke_notFound_throws() {
            UUID nonExistentId = UUID.randomUUID();
            when(consentRepo.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> crudService.revoke(nonExistentId, "reason", ACTOR_ID))
                    .isInstanceOf(ConsentNotFoundException.class);
        }
    }

    // =========================================================================
    // Find tests
    // =========================================================================

    @Nested
    @DisplayName("Find consent directives")
    class FindTests {

        @Test
        @DisplayName("findBySubject_filtersByTenantAndCpid - returns paginated results for patient")
        void findBySubject_filtersByTenantAndCpid() {
            ConsentDirectiveEntity entity = buildPersistedEntity();
            ConsentDirectiveDto dto = buildDto(entity);
            Pageable pageable = PageRequest.of(0, 10);
            Page<ConsentDirectiveEntity> entityPage = new PageImpl<>(List.of(entity), pageable, 1);

            when(consentRepo.findByTenantIdAndPatientRefAndStatus(TENANT_ID, PATIENT_REF, "ACTIVE", pageable))
                    .thenReturn(entityPage);
            when(fhirMapper.toDto(entity)).thenReturn(dto);

            Page<ConsentDirectiveDto> result = crudService.findByPatient(
                    TENANT_ID, PATIENT_REF, "ACTIVE", pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).hasSize(1);

            ConsentDirectiveDto resultDto = result.getContent().get(0);
            assertThat(resultDto.tenantId()).isEqualTo(TENANT_ID);
            assertThat(resultDto.patientRef()).isEqualTo(PATIENT_REF);
            assertThat(resultDto.status()).isEqualTo("ACTIVE");

            // Verify the repository was called with the correct tenant and patient filters
            verify(consentRepo).findByTenantIdAndPatientRefAndStatus(TENANT_ID, PATIENT_REF, "ACTIVE", pageable);
        }

        @Test
        @DisplayName("findById_returnsDto - returns DTO for existing consent")
        void findById_returnsDto() {
            ConsentDirectiveEntity entity = buildPersistedEntity();
            UUID consentId = entity.getId();
            ConsentDirectiveDto dto = buildDto(entity);

            when(consentRepo.findById(consentId)).thenReturn(Optional.of(entity));
            when(fhirMapper.toDto(entity)).thenReturn(dto);

            ConsentDirectiveDto result = crudService.findById(consentId);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(consentId);
        }

        @Test
        @DisplayName("findById_notFound_throws - throws for non-existent consent")
        void findById_notFound_throws() {
            UUID nonExistentId = UUID.randomUUID();
            when(consentRepo.findById(nonExistentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> crudService.findById(nonExistentId))
                    .isInstanceOf(ConsentNotFoundException.class);
        }

        @Test
        @DisplayName("findFhirJsonById_returnsRawJson - returns raw FHIR JSON for existing consent")
        void findFhirJsonById_returnsRawJson() {
            ConsentDirectiveEntity entity = buildPersistedEntity();
            UUID consentId = entity.getId();

            when(consentRepo.findById(consentId)).thenReturn(Optional.of(entity));

            String result = crudService.findFhirJsonById(consentId);

            assertThat(result).isEqualTo(FHIR_JSON);
        }
    }
}
