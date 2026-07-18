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
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.ProvisionalCpidResponse;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.ReconcileRequest;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.ReconcileResponse;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.IdMappingEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.ProvisionalCpidEntity;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.ProvisionalCpidRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReconciliationService}.
 *
 * <p>Validates offline O-CPID provisioning, reconciliation to canonical CPIDs,
 * idempotent reconciliation, and listing of unreconciled entries.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReconciliationService")
class ReconciliationServiceTest {

    @Mock
    private ProvisionalCpidRepository provisionalRepo;

    @Mock
    private EventOutboxRepository outboxRepo;

    @Mock
    private CpidGenerator cpidGenerator;

    @Mock
    private IdResolutionService idResolutionService;

    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new ReconciliationService(
                provisionalRepo, outboxRepo, cpidGenerator, idResolutionService, objectMapper);
    }

    // ── Fixture helpers ────────────────────────────────────────────────────

    private static UUID tenantId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    private static UUID facilityId() {
        return UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    }

    private static UUID healthId() {
        return UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    }

    private static UUID oCpid() {
        return UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    }

    private static UUID canonicalCpid() {
        return UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    }

    private static String deviceFingerprint() {
        return "device-fp-abc123";
    }

    private ProvisionalCpidEntity buildProvisionalEntity(String status) {
        ProvisionalCpidEntity entity = new ProvisionalCpidEntity();
        entity.setId(1L);
        entity.setTenantId(tenantId());
        entity.setOriginCpid(oCpid());
        entity.setFacilityId(facilityId());
        entity.setDeviceFingerprint(deviceFingerprint());
        entity.setStatus(status);
        entity.setIssuedAt(Instant.now().minusSeconds(3600));
        if ("RECONCILED".equals(status)) {
            entity.setCanonicalCpid(canonicalCpid());
            entity.setReconciledAt(Instant.now().minusSeconds(600));
        }
        return entity;
    }

    // ── createProvisionalCpid tests ────────────────────────────────────────

    @Nested
    @DisplayName("createProvisionalCpid")
    class CreateProvisionalCpid {

        @Test
        @DisplayName("generates a random O-CPID and persists it")
        void createProvisional_generatesAndPersists() {
            UUID generatedOCpid = UUID.randomUUID();
            when(cpidGenerator.generateProvisionalCpid()).thenReturn(generatedOCpid);
            when(provisionalRepo.save(any(ProvisionalCpidEntity.class)))
                    .thenAnswer(invocation -> {
                        ProvisionalCpidEntity e = invocation.getArgument(0);
                        e.setId(1L);
                        e.setIssuedAt(Instant.now());
                        return e;
                    });

            ProvisionalCpidResponse response = service.createProvisionalCpid(
                    tenantId(), facilityId(), deviceFingerprint());

            assertEquals(generatedOCpid, response.oCpid());
            assertEquals(facilityId(), response.facilityId());
            assertEquals(deviceFingerprint(), response.deviceFingerprint());
            assertEquals("PROVISIONAL", response.status());
            assertNull(response.canonicalCpid(),
                    "Canonical CPID must be null for a new provisional entry");
        }

        @Test
        @DisplayName("saved entity has correct fields")
        void createProvisional_entityFieldsCorrect() {
            UUID generatedOCpid = UUID.randomUUID();
            when(cpidGenerator.generateProvisionalCpid()).thenReturn(generatedOCpid);
            when(provisionalRepo.save(any(ProvisionalCpidEntity.class)))
                    .thenAnswer(invocation -> {
                        ProvisionalCpidEntity e = invocation.getArgument(0);
                        e.setId(1L);
                        e.setIssuedAt(Instant.now());
                        return e;
                    });

            service.createProvisionalCpid(tenantId(), facilityId(), deviceFingerprint());

            ArgumentCaptor<ProvisionalCpidEntity> captor =
                    ArgumentCaptor.forClass(ProvisionalCpidEntity.class);
            verify(provisionalRepo).save(captor.capture());
            ProvisionalCpidEntity saved = captor.getValue();

            assertEquals(tenantId(), saved.getTenantId());
            assertEquals(generatedOCpid, saved.getOriginCpid());
            assertEquals(facilityId(), saved.getFacilityId());
            assertEquals(deviceFingerprint(), saved.getDeviceFingerprint());
            assertEquals("PROVISIONAL", saved.getStatus());
        }

        @Test
        @DisplayName("publishes OCPID_CREATED outbox event")
        void createProvisional_publishesOutboxEvent() {
            UUID generatedOCpid = UUID.randomUUID();
            when(cpidGenerator.generateProvisionalCpid()).thenReturn(generatedOCpid);
            when(provisionalRepo.save(any(ProvisionalCpidEntity.class)))
                    .thenAnswer(invocation -> {
                        ProvisionalCpidEntity e = invocation.getArgument(0);
                        e.setId(1L);
                        e.setIssuedAt(Instant.now());
                        return e;
                    });

            service.createProvisionalCpid(tenantId(), facilityId(), deviceFingerprint());

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepo).save(captor.capture());
            EventOutboxEntity event = captor.getValue();

            assertEquals("ProvisionalCpid", event.getAggregateType());
            assertEquals("OCPID_CREATED", event.getEventType());
            assertEquals(generatedOCpid.toString(), event.getAggregateId());
        }
    }

    // ── reconcile tests ────────────────────────────────────────────────────

    /** Builds the id_mapping row the resolution service would find-or-create. */
    private IdMappingEntity buildMapping(UUID cpid) {
        IdMappingEntity mapping = new IdMappingEntity();
        mapping.setId(1L);
        mapping.setTenantId(tenantId());
        mapping.setHealthId(healthId());
        mapping.setCpid(cpid);
        mapping.setMappingStatus("ACTIVE");
        mapping.setCreatedAt(Instant.now());
        return mapping;
    }

    @Nested
    @DisplayName("reconcile")
    class Reconcile {

        private void stubMappingResolution() {
            when(idResolutionService.findOrCreateMapping(tenantId(), healthId(), null))
                    .thenReturn(buildMapping(canonicalCpid()));
        }

        @Test
        @DisplayName("maps O-CPID to the canonical CPID held by id_mapping")
        void reconcile_newReconciliation_mapsToCanonical() {
            ProvisionalCpidEntity provisional = buildProvisionalEntity("PROVISIONAL");
            when(provisionalRepo.findByTenantIdAndOriginCpid(tenantId(), oCpid()))
                    .thenReturn(Optional.of(provisional));
            stubMappingResolution();
            when(provisionalRepo.save(any(ProvisionalCpidEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ReconcileRequest request = new ReconcileRequest(tenantId(), oCpid(), healthId());
            ReconcileResponse response = service.reconcile(request);

            assertEquals(oCpid(), response.oCpid());
            assertEquals(canonicalCpid(), response.canonicalCpid());
            assertEquals("RECONCILED", response.status());
            assertNotNull(response.reconciledAt(), "reconciledAt must be set");
        }

        @Test
        @DisplayName("updates provisional entity status to RECONCILED")
        void reconcile_updatesProvisionalEntity() {
            ProvisionalCpidEntity provisional = buildProvisionalEntity("PROVISIONAL");
            when(provisionalRepo.findByTenantIdAndOriginCpid(tenantId(), oCpid()))
                    .thenReturn(Optional.of(provisional));
            stubMappingResolution();
            when(provisionalRepo.save(any(ProvisionalCpidEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.reconcile(new ReconcileRequest(tenantId(), oCpid(), healthId()));

            ArgumentCaptor<ProvisionalCpidEntity> captor =
                    ArgumentCaptor.forClass(ProvisionalCpidEntity.class);
            verify(provisionalRepo).save(captor.capture());
            ProvisionalCpidEntity saved = captor.getValue();

            assertEquals("RECONCILED", saved.getStatus());
            assertEquals(canonicalCpid(), saved.getCanonicalCpid());
            assertNotNull(saved.getReconciledAt());
        }

        @Test
        @DisplayName("delegates mapping creation to IdResolutionService (single mint path)")
        void reconcile_delegatesToResolutionService() {
            ProvisionalCpidEntity provisional = buildProvisionalEntity("PROVISIONAL");
            when(provisionalRepo.findByTenantIdAndOriginCpid(tenantId(), oCpid()))
                    .thenReturn(Optional.of(provisional));
            stubMappingResolution();
            when(provisionalRepo.save(any(ProvisionalCpidEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.reconcile(new ReconcileRequest(tenantId(), oCpid(), healthId()));

            verify(idResolutionService).findOrCreateMapping(tenantId(), healthId(), null);
        }

        @Test
        @DisplayName("adopts the existing mapping's CPID — never forks a fresh value")
        void reconcile_existingMapping_adoptsMappedCpid() {
            // Regression for the pre-decoupling bug: the service used to stamp a
            // freshly generated CPID even when a mapping already existed. Under
            // random CPIDs that would fork the patient's clinical key.
            UUID mappedCpid = UUID.fromString("99999999-9999-4999-8999-999999999999");
            ProvisionalCpidEntity provisional = buildProvisionalEntity("PROVISIONAL");
            when(provisionalRepo.findByTenantIdAndOriginCpid(tenantId(), oCpid()))
                    .thenReturn(Optional.of(provisional));
            when(idResolutionService.findOrCreateMapping(tenantId(), healthId(), null))
                    .thenReturn(buildMapping(mappedCpid));
            when(provisionalRepo.save(any(ProvisionalCpidEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ReconcileResponse response = service.reconcile(
                    new ReconcileRequest(tenantId(), oCpid(), healthId()));

            assertEquals(mappedCpid, response.canonicalCpid(),
                    "Canonical CPID must be the mapping's value, not a new mint");
        }

        @Test
        @DisplayName("returns existing result if already reconciled (idempotent)")
        void reconcile_alreadyReconciled_returnsExisting() {
            ProvisionalCpidEntity alreadyReconciled = buildProvisionalEntity("RECONCILED");
            when(provisionalRepo.findByTenantIdAndOriginCpid(tenantId(), oCpid()))
                    .thenReturn(Optional.of(alreadyReconciled));

            ReconcileRequest request = new ReconcileRequest(tenantId(), oCpid(), healthId());
            ReconcileResponse response = service.reconcile(request);

            assertEquals(oCpid(), response.oCpid());
            assertEquals(canonicalCpid(), response.canonicalCpid());
            assertEquals("RECONCILED", response.status());
            assertNotNull(response.reconciledAt());

            // Should NOT resolve a mapping or save anything
            verify(idResolutionService, never()).findOrCreateMapping(any(), any(), any());
            verify(provisionalRepo, never()).save(any());
        }

        @Test
        @DisplayName("throws IdentityNotFoundException for unknown O-CPID")
        void reconcile_unknownOCpid_throws() {
            when(provisionalRepo.findByTenantIdAndOriginCpid(tenantId(), oCpid()))
                    .thenReturn(Optional.empty());

            ReconcileRequest request = new ReconcileRequest(tenantId(), oCpid(), healthId());

            assertThrows(IdentityNotFoundException.class,
                    () -> service.reconcile(request),
                    "Must throw IdentityNotFoundException for unknown O-CPID");
        }

        @Test
        @DisplayName("publishes OCPID_RECONCILED outbox event")
        void reconcile_publishesOutboxEvent() {
            ProvisionalCpidEntity provisional = buildProvisionalEntity("PROVISIONAL");
            when(provisionalRepo.findByTenantIdAndOriginCpid(tenantId(), oCpid()))
                    .thenReturn(Optional.of(provisional));
            stubMappingResolution();
            when(provisionalRepo.save(any(ProvisionalCpidEntity.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            service.reconcile(new ReconcileRequest(tenantId(), oCpid(), healthId()));

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepo).save(captor.capture());
            EventOutboxEntity event = captor.getValue();

            assertEquals("ProvisionalCpid", event.getAggregateType());
            assertEquals("OCPID_RECONCILED", event.getEventType());
            assertEquals(oCpid().toString(), event.getAggregateId());
        }
    }

    // ── listUnreconciled tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("listUnreconciled")
    class ListUnreconciled {

        @Test
        @DisplayName("returns only PROVISIONAL entries for the given tenant")
        void listUnreconciled_returnsProvisionalOnly() {
            ProvisionalCpidEntity e1 = buildProvisionalEntity("PROVISIONAL");
            e1.setOriginCpid(UUID.randomUUID());
            ProvisionalCpidEntity e2 = buildProvisionalEntity("PROVISIONAL");
            e2.setOriginCpid(UUID.randomUUID());

            when(provisionalRepo.findByTenantIdAndStatus(tenantId(), "PROVISIONAL"))
                    .thenReturn(List.of(e1, e2));

            List<ProvisionalCpidResponse> result = service.listUnreconciled(tenantId());

            assertEquals(2, result.size(), "Should return exactly 2 unreconciled entries");
            result.forEach(r -> assertEquals("PROVISIONAL", r.status()));
        }

        @Test
        @DisplayName("returns empty list when no unreconciled entries exist")
        void listUnreconciled_empty_returnsEmptyList() {
            when(provisionalRepo.findByTenantIdAndStatus(tenantId(), "PROVISIONAL"))
                    .thenReturn(List.of());

            List<ProvisionalCpidResponse> result = service.listUnreconciled(tenantId());

            assertTrue(result.isEmpty(), "Must return empty list when no provisional entries exist");
        }

        @Test
        @DisplayName("response entries contain facility and device info")
        void listUnreconciled_entriesContainFacilityInfo() {
            ProvisionalCpidEntity entity = buildProvisionalEntity("PROVISIONAL");
            when(provisionalRepo.findByTenantIdAndStatus(tenantId(), "PROVISIONAL"))
                    .thenReturn(List.of(entity));

            List<ProvisionalCpidResponse> result = service.listUnreconciled(tenantId());

            assertEquals(1, result.size());
            ProvisionalCpidResponse entry = result.get(0);
            assertEquals(facilityId(), entry.facilityId());
            assertEquals(deviceFingerprint(), entry.deviceFingerprint());
            assertNull(entry.canonicalCpid(),
                    "Unreconciled entry must not have a canonical CPID");
            assertNull(entry.reconciledAt(),
                    "Unreconciled entry must not have a reconciledAt timestamp");
        }
    }
}
