package zw.gov.mohcc.impilo.mvumo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentEventRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.DelegationRelationshipEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.DelegationRelationshipRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DelegationServiceTest {

    @Mock private DelegationRelationshipRepository repository;
    @Mock private ConsentEventRepository consentEventRepository;
    @Mock private EventOutboxRepository eventOutboxRepository;

    private DelegationService service;
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @BeforeEach
    void setUp() {
        service = new DelegationService(repository, consentEventRepository, eventOutboxRepository, new ObjectMapper());
        when(repository.save(any())).thenAnswer(inv -> {
            DelegationRelationshipEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    @Test
    void create_facilityAssignment_grantsAndAudits() {
        Map<String, Object> view = service.create(TENANT, Map.of(
                "delegatorSubjectRef", "Patient/cpid-1", "delegateActorId", "chw-7",
                "relationshipType", "chw", "legalBasis", "facility_assignment",
                "scope", List.of("appointments:read"), "minDelegateLoa", 2));

        assertEquals("GRANTED", view.get("status"));
        assertEquals("CHW", view.get("relationshipType"));
        verify(consentEventRepository).save(any());
        verify(eventOutboxRepository).save(any());
    }

    @Test
    void create_consentBasisWithoutBackingGrant_rejected() {
        assertThrows(ResponseStatusException.class, () -> service.create(TENANT, Map.of(
                "delegatorSubjectRef", "Patient/cpid-1", "delegateActorId", "guardian-1",
                "relationshipType", "GUARDIAN", "legalBasis", "CONSENT")));
    }

    @Test
    void create_unknownRelationshipType_rejected() {
        assertThrows(ResponseStatusException.class, () -> service.create(TENANT, Map.of(
                "delegatorSubjectRef", "s", "delegateActorId", "d",
                "relationshipType", "FRIEND", "legalBasis", "CONSENT", "backingConsentRequestId", UUID.randomUUID().toString())));
    }

    @Test
    void resolve_activeUnexpired_returnsFacts() {
        DelegationRelationshipEntity e = granted("Patient/cpid-1", "chw-7",
                Instant.now().plus(1, ChronoUnit.DAYS));
        when(repository.findByTenantIdAndDelegateActorIdAndDelegatorSubjectRefAndStatus(
                eq(TENANT), eq("chw-7"), eq("Patient/cpid-1"), eq("GRANTED")))
                .thenReturn(List.of(e));

        Map<String, Object> r = service.resolve(TENANT, "chw-7", "Patient/cpid-1");

        assertTrue((Boolean) r.get("active"));
        assertEquals("Patient/cpid-1", r.get("principalId"));
        assertEquals(2, r.get("assuranceFloor"));
        assertTrue(((List<?>) r.get("scope")).contains("appointments:read"));
    }

    @Test
    void resolve_expired_isInactive() {
        DelegationRelationshipEntity e = granted("Patient/cpid-1", "chw-7",
                Instant.now().minus(1, ChronoUnit.DAYS));
        when(repository.findByTenantIdAndDelegateActorIdAndDelegatorSubjectRefAndStatus(
                eq(TENANT), eq("chw-7"), eq("Patient/cpid-1"), eq("GRANTED")))
                .thenReturn(List.of(e));

        assertFalse((Boolean) service.resolve(TENANT, "chw-7", "Patient/cpid-1").get("active"));
    }

    @Test
    void resolve_noRelationship_isInactive() {
        when(repository.findByTenantIdAndDelegateActorIdAndDelegatorSubjectRefAndStatus(any(), any(), any(), any()))
                .thenReturn(List.of());
        assertFalse((Boolean) service.resolve(TENANT, "x", "y").get("active"));
    }

    @Test
    void revoke_setsWithdrawn_andAudits() {
        DelegationRelationshipEntity e = granted("Patient/cpid-1", "chw-7", null);
        e.setId(UUID.randomUUID());
        when(repository.findByIdAndTenantId(eq(e.getId()), eq(TENANT))).thenReturn(java.util.Optional.of(e));

        Map<String, Object> r = service.revoke(TENANT, e.getId(), "no longer caregiver");

        assertEquals("WITHDRAWN", r.get("status"));
        verify(eventOutboxRepository).save(any());
    }

    private DelegationRelationshipEntity granted(String subject, String delegate, Instant expiresAt) {
        DelegationRelationshipEntity e = new DelegationRelationshipEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(TENANT);
        e.setDelegatorSubjectRef(subject);
        e.setDelegateActorId(delegate);
        e.setRelationshipType("CHW");
        e.setLegalBasis("FACILITY_ASSIGNMENT");
        e.setScope("[\"appointments:read\"]");
        e.setMinDelegateLoa(2);
        e.setStatus(DelegationRelationshipEntity.Status.GRANTED.name());
        e.setExpiresAt(expiresAt);
        return e;
    }
}
