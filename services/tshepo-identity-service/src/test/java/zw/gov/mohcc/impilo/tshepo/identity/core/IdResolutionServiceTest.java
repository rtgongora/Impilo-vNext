package zw.gov.mohcc.impilo.tshepo.identity.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.*;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.IdMappingEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.IdMappingRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link IdResolutionService}.
 *
 * <p>Validates the full resolution chain (Impilo ID hash -> Health ID via VITO -> CPID),
 * direct lookup by Health ID and CPID, mapping creation, and error handling.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdResolutionService")
class IdResolutionServiceTest {

    @Mock
    private IdMappingRepository mappingRepo;

    @Mock
    private EventOutboxRepository outboxRepo;

    @Mock
    private CpidGenerator cpidGenerator;

    @Mock
    private RestTemplate vitoRestTemplate;

    private IdResolutionService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new IdResolutionService(
                mappingRepo, outboxRepo, cpidGenerator, vitoRestTemplate, objectMapper);
    }

    // ── Fixture helpers ────────────────────────────────────────────────────

    private static UUID tenantId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    private static UUID healthId() {
        return UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    }

    private static UUID cpid() {
        return UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    }

    private static UUID crid() {
        return UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    }

    private static String impiloIdHash() {
        return "sha256:abc123def456";
    }

    private IdMappingEntity buildMappingEntity() {
        IdMappingEntity entity = new IdMappingEntity();
        entity.setId(1L);
        entity.setTenantId(tenantId());
        entity.setHealthId(healthId());
        entity.setCpid(cpid());
        entity.setCrid(crid());
        entity.setMappingStatus("ACTIVE");
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    /**
     * Stubs the VITO REST call to return a Health ID for the given Impilo ID hash.
     */
    private void stubVitoResolveSuccess() {
        String responseBody = String.format(
                "{\"data\": {\"healthId\": \"%s\"}}", healthId());
        when(vitoRestTemplate.postForEntity(eq("/v1/registry/resolve"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));
    }

    /**
     * Stubs the VITO REST call to return 404 Not Found.
     */
    private void stubVitoResolveNotFound() {
        when(vitoRestTemplate.postForEntity(eq("/v1/registry/resolve"), any(), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", null, null, null));
    }

    // ── resolve (full chain) tests ─────────────────────────────────────────

    @Nested
    @DisplayName("resolve (Impilo ID hash -> Health ID -> CPID)")
    class Resolve {

        @Test
        @DisplayName("resolves Impilo ID hash to CPID via VITO, creating new mapping")
        void resolve_newMapping_createsCpidMapping() {
            stubVitoResolveSuccess();
            when(mappingRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.empty());
            when(cpidGenerator.generateCpid(tenantId(), healthId())).thenReturn(cpid());
            when(mappingRepo.save(any(IdMappingEntity.class)))
                    .thenAnswer(invocation -> {
                        IdMappingEntity e = invocation.getArgument(0);
                        e.setId(1L);
                        e.setCreatedAt(Instant.now());
                        return e;
                    });

            ResolveRequest request = new ResolveRequest(tenantId(), impiloIdHash());
            ResolveResponse response = service.resolve(request);

            assertEquals(healthId(), response.healthId());
            assertEquals(cpid(), response.cpid());
            assertEquals("ACTIVE", response.mappingStatus());
        }

        @Test
        @DisplayName("returns existing mapping when CPID already exists for Health ID")
        void resolve_existingMapping_returnsExisting() {
            stubVitoResolveSuccess();
            IdMappingEntity existing = buildMappingEntity();
            when(mappingRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.of(existing));

            ResolveRequest request = new ResolveRequest(tenantId(), impiloIdHash());
            ResolveResponse response = service.resolve(request);

            assertEquals(healthId(), response.healthId());
            assertEquals(cpid(), response.cpid());
            assertEquals(crid(), response.crid());
            // Should NOT generate a new CPID or save a new entity
            verify(cpidGenerator, never()).generateCpid(any(), any());
        }

        @Test
        @DisplayName("throws IdentityNotFoundException when VITO returns 404")
        void resolve_vitoNotFound_throws() {
            stubVitoResolveNotFound();

            ResolveRequest request = new ResolveRequest(tenantId(), impiloIdHash());

            assertThrows(IdentityNotFoundException.class,
                    () -> service.resolve(request),
                    "Must throw IdentityNotFoundException when VITO cannot resolve the Impilo ID hash");
        }

        @Test
        @DisplayName("throws IdentityNotFoundException when VITO returns empty body")
        void resolve_vitoEmptyBody_throws() {
            when(vitoRestTemplate.postForEntity(eq("/v1/registry/resolve"), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

            ResolveRequest request = new ResolveRequest(tenantId(), impiloIdHash());

            assertThrows(IdentityNotFoundException.class,
                    () -> service.resolve(request),
                    "Must throw when VITO returns null body");
        }

        @Test
        @DisplayName("throws IdentityNotFoundException when VITO response has missing healthId field")
        void resolve_vitoMissingHealthIdField_throws() {
            String responseBody = "{\"data\": {}}";
            when(vitoRestTemplate.postForEntity(eq("/v1/registry/resolve"), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(responseBody, HttpStatus.OK));

            ResolveRequest request = new ResolveRequest(tenantId(), impiloIdHash());

            assertThrows(IdentityNotFoundException.class,
                    () -> service.resolve(request),
                    "Must throw when VITO response is missing the healthId field");
        }

        @Test
        @DisplayName("publishes MAPPING_CREATED outbox event for new mappings")
        void resolve_newMapping_publishesOutboxEvent() {
            stubVitoResolveSuccess();
            when(mappingRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.empty());
            when(cpidGenerator.generateCpid(tenantId(), healthId())).thenReturn(cpid());
            when(mappingRepo.save(any(IdMappingEntity.class)))
                    .thenAnswer(invocation -> {
                        IdMappingEntity e = invocation.getArgument(0);
                        e.setId(1L);
                        e.setCreatedAt(Instant.now());
                        return e;
                    });

            service.resolve(new ResolveRequest(tenantId(), impiloIdHash()));

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepo).save(captor.capture());
            EventOutboxEntity event = captor.getValue();

            assertEquals("IdMapping", event.getAggregateType());
            assertEquals("MAPPING_CREATED", event.getEventType());
            assertEquals(cpid().toString(), event.getAggregateId());
        }
    }

    // ── getMappingByHealthId tests ──────────────────────────────────────────

    @Nested
    @DisplayName("getMappingByHealthId")
    class GetMappingByHealthId {

        @Test
        @DisplayName("returns mapping when found")
        void getMappingByHealthId_found_returnsMapping() {
            IdMappingEntity entity = buildMappingEntity();
            when(mappingRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.of(entity));

            MappingResponse response = service.getMappingByHealthId(tenantId(), healthId());

            assertEquals(healthId(), response.healthId());
            assertEquals(cpid(), response.cpid());
            assertEquals(crid(), response.crid());
            assertEquals("ACTIVE", response.mappingStatus());
        }

        @Test
        @DisplayName("throws IdentityNotFoundException when no mapping exists")
        void getMappingByHealthId_notFound_throws() {
            when(mappingRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.empty());

            assertThrows(IdentityNotFoundException.class,
                    () -> service.getMappingByHealthId(tenantId(), healthId()),
                    "Must throw IdentityNotFoundException for unknown Health ID");
        }
    }

    // ── getMappingByCpid tests ──────────────────────────────────────────────

    @Nested
    @DisplayName("getMappingByCpid (reverse lookup)")
    class GetMappingByCpid {

        @Test
        @DisplayName("returns mapping when found by CPID")
        void getMappingByCpid_found_returnsMapping() {
            IdMappingEntity entity = buildMappingEntity();
            when(mappingRepo.findByTenantIdAndCpid(tenantId(), cpid()))
                    .thenReturn(Optional.of(entity));

            MappingResponse response = service.getMappingByCpid(tenantId(), cpid());

            assertEquals(healthId(), response.healthId());
            assertEquals(cpid(), response.cpid());
            assertEquals("ACTIVE", response.mappingStatus());
        }

        @Test
        @DisplayName("throws IdentityNotFoundException for unknown CPID")
        void getMappingByCpid_notFound_throws() {
            when(mappingRepo.findByTenantIdAndCpid(tenantId(), cpid()))
                    .thenReturn(Optional.empty());

            assertThrows(IdentityNotFoundException.class,
                    () -> service.getMappingByCpid(tenantId(), cpid()),
                    "Must throw IdentityNotFoundException for unknown CPID");
        }
    }

    // ── createMapping tests ────────────────────────────────────────────────

    @Nested
    @DisplayName("createMapping")
    class CreateMapping {

        @Test
        @DisplayName("creates new mapping with deterministic CPID when none exists")
        void createMapping_new_generatesAndStoresCpid() {
            when(mappingRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.empty());
            when(cpidGenerator.generateCpid(tenantId(), healthId())).thenReturn(cpid());
            when(mappingRepo.save(any(IdMappingEntity.class)))
                    .thenAnswer(invocation -> {
                        IdMappingEntity e = invocation.getArgument(0);
                        e.setId(1L);
                        e.setCreatedAt(Instant.now());
                        return e;
                    });

            CreateMappingRequest request = new CreateMappingRequest(tenantId(), healthId(), crid());
            MappingResponse response = service.createMapping(request);

            assertEquals(healthId(), response.healthId());
            assertEquals(cpid(), response.cpid());
            assertEquals(crid(), response.crid());
            assertEquals("ACTIVE", response.mappingStatus());
        }

        @Test
        @DisplayName("returns existing mapping if one already exists (idempotent)")
        void createMapping_existing_returnsExisting() {
            IdMappingEntity existing = buildMappingEntity();
            when(mappingRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.of(existing));

            CreateMappingRequest request = new CreateMappingRequest(tenantId(), healthId(), null);
            MappingResponse response = service.createMapping(request);

            assertEquals(cpid(), response.cpid());
            verify(cpidGenerator, never()).generateCpid(any(), any());
            // Should not save a new entity
            verify(mappingRepo, never()).save(any());
        }

        @Test
        @DisplayName("saved entity has correct tenant, health, cpid, and crid fields")
        void createMapping_new_entityFieldsCorrect() {
            when(mappingRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.empty());
            when(cpidGenerator.generateCpid(tenantId(), healthId())).thenReturn(cpid());
            when(mappingRepo.save(any(IdMappingEntity.class)))
                    .thenAnswer(invocation -> {
                        IdMappingEntity e = invocation.getArgument(0);
                        e.setId(1L);
                        e.setCreatedAt(Instant.now());
                        return e;
                    });

            service.createMapping(new CreateMappingRequest(tenantId(), healthId(), crid()));

            ArgumentCaptor<IdMappingEntity> captor = ArgumentCaptor.forClass(IdMappingEntity.class);
            verify(mappingRepo).save(captor.capture());
            IdMappingEntity saved = captor.getValue();

            assertEquals(tenantId(), saved.getTenantId());
            assertEquals(healthId(), saved.getHealthId());
            assertEquals(cpid(), saved.getCpid());
            assertEquals(crid(), saved.getCrid());
            assertEquals("ACTIVE", saved.getMappingStatus());
        }

        @Test
        @DisplayName("handles null CRID gracefully")
        void createMapping_nullCrid_succeeds() {
            when(mappingRepo.findByTenantIdAndHealthId(tenantId(), healthId()))
                    .thenReturn(Optional.empty());
            when(cpidGenerator.generateCpid(tenantId(), healthId())).thenReturn(cpid());
            when(mappingRepo.save(any(IdMappingEntity.class)))
                    .thenAnswer(invocation -> {
                        IdMappingEntity e = invocation.getArgument(0);
                        e.setId(1L);
                        e.setCreatedAt(Instant.now());
                        return e;
                    });

            CreateMappingRequest request = new CreateMappingRequest(tenantId(), healthId(), null);
            MappingResponse response = service.createMapping(request);

            assertEquals(cpid(), response.cpid());
            assertNull(response.crid(), "CRID should be null when not provided");
        }
    }
}
