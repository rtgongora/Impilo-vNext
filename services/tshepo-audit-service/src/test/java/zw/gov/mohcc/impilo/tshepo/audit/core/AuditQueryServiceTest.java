package zw.gov.mohcc.impilo.tshepo.audit.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AccessHistoryEntry;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditEventQueryParams;
import zw.gov.mohcc.impilo.tshepo.audit.api.dto.AuditEventResponse;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.entity.AuditEventEntity;
import zw.gov.mohcc.impilo.tshepo.audit.persistence.repository.AuditEventRepository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuditQueryService}.
 *
 * Validates paginated query, tenant-isolated filtering, DTO mapping,
 * and patient-visible access history (redacted output).
 */
@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_TENANT = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID EVENT_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID FACILITY_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Mock
    private AuditEventRepository eventRepository;

    @InjectMocks
    private AuditQueryService auditQueryService;

    @Captor
    private ArgumentCaptor<Pageable> pageableCaptor;

    // -----------------------------------------------------------------------
    // Helper methods
    // -----------------------------------------------------------------------

    private AuditEventEntity buildEvent(UUID tenantId, String actorId, String eventType,
                                        String subjectRef, UUID correlationId) {
        AuditEventEntity event = new AuditEventEntity();
        event.setId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setEventType(eventType);
        event.setActorId(actorId);
        event.setActorType("PRACTITIONER");
        event.setSubjectRef(subjectRef);
        event.setResourceType("Encounter");
        event.setResourceId("enc-001");
        event.setAction("READ");
        event.setOutcome("SUCCESS");
        event.setPurposeOfUse("TREATMENT");
        event.setFacilityId(FACILITY_ID);
        event.setCorrelationId(correlationId);
        event.setDetail("{\"note\":\"test\"}");
        event.setPreviousHash("aabbccdd00112233aabbccdd00112233aabbccdd00112233aabbccdd00112233");
        event.setEntryHash("11223344556677881122334455667788112233445566778811223344556677aa");
        event.setSequenceNumber(1L);
        event.setCreatedAt(Instant.parse("2025-06-15T08:30:00Z"));
        return event;
    }

    private AuditEventEntity buildEvent(UUID tenantId, String actorId, String subjectRef) {
        return buildEvent(tenantId, actorId, "AUTH_DECISION", subjectRef, UUID.randomUUID());
    }

    // -----------------------------------------------------------------------
    // queryEvents / findByCorrelation tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("queryEvents returns matching events filtered by subjectRef (correlation proxy)")
    @SuppressWarnings("unchecked")
    void findByCorrelation_returnsMatchingEvents() {
        // Given: two events with different correlations but same subject
        UUID correlationA = UUID.fromString("cccccccc-cccc-cccc-cccc-aaaaaaaaaaaa");
        UUID correlationB = UUID.fromString("cccccccc-cccc-cccc-cccc-bbbbbbbbbbbb");

        AuditEventEntity event1 = buildEvent(TENANT_ID, "user-1", "AUTH_DECISION",
                "Patient/CPID-100", correlationA);
        AuditEventEntity event2 = buildEvent(TENANT_ID, "user-2", "DATA_ACCESS",
                "Patient/CPID-100", correlationB);

        Page<AuditEventEntity> page = new PageImpl<>(List.of(event1, event2));
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        AuditEventQueryParams params = new AuditEventQueryParams(
                TENANT_ID, null, "Patient/CPID-100", null, null, null, 0, 20);

        // When
        Page<AuditEventResponse> result = auditQueryService.queryEvents(params);

        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent())
                .allSatisfy(response -> {
                    assertThat(response.tenantId()).isEqualTo(TENANT_ID);
                    assertThat(response.subjectRef()).isEqualTo("Patient/CPID-100");
                });

        // Verify correlation IDs are preserved in the response
        List<UUID> correlationIds = result.getContent().stream()
                .map(AuditEventResponse::correlationId)
                .toList();
        assertThat(correlationIds).containsExactlyInAnyOrder(correlationA, correlationB);
    }

    @Test
    @DisplayName("queryEvents with no results returns empty page")
    @SuppressWarnings("unchecked")
    void findByCorrelation_noMatches_returnsEmptyPage() {
        Page<AuditEventEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        AuditEventQueryParams params = new AuditEventQueryParams(
                TENANT_ID, null, "Patient/NONEXISTENT", null, null, null, 0, 20);

        Page<AuditEventResponse> result = auditQueryService.queryEvents(params);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // -----------------------------------------------------------------------
    // findByActor tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("queryEvents filters by tenant and actor, returns only matching events")
    @SuppressWarnings("unchecked")
    void findByActor_filtersByTenantAndActor() {
        // Given: one event matching the actor filter
        AuditEventEntity event = buildEvent(TENANT_ID, "dr-jones", "Patient/CPID-200");
        event.setSequenceNumber(42L);

        Page<AuditEventEntity> page = new PageImpl<>(List.of(event));
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        AuditEventQueryParams params = new AuditEventQueryParams(
                TENANT_ID, "dr-jones", null, null, null, null, 0, 20);

        // When
        Page<AuditEventResponse> result = auditQueryService.queryEvents(params);

        // Then
        assertThat(result.getContent()).hasSize(1);
        AuditEventResponse response = result.getContent().get(0);

        assertThat(response.tenantId()).isEqualTo(TENANT_ID);
        assertThat(response.actorId()).isEqualTo("dr-jones");
        assertThat(response.actorType()).isEqualTo("PRACTITIONER");
        assertThat(response.action()).isEqualTo("READ");
        assertThat(response.outcome()).isEqualTo("SUCCESS");
        assertThat(response.sequenceNumber()).isEqualTo(42L);

        // Verify specification-based findAll was invoked
        verify(eventRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("queryEvents uses descending sequenceNumber sort order")
    @SuppressWarnings("unchecked")
    void findByActor_usesDescendingSequenceNumberSort() {
        Page<AuditEventEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        AuditEventQueryParams params = new AuditEventQueryParams(
                TENANT_ID, "user-1", null, null, null, null, 0, 50);

        auditQueryService.queryEvents(params);

        verify(eventRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();

        assertThat(captured.getPageNumber()).isEqualTo(0);
        assertThat(captured.getPageSize()).isEqualTo(50);
        assertThat(captured.getSort().getOrderFor("sequenceNumber")).isNotNull();
        assertThat(captured.getSort().getOrderFor("sequenceNumber").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("queryEvents with time range filters passes correct pageable parameters")
    @SuppressWarnings("unchecked")
    void queryEvents_withTimeRangeFilters() {
        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-12-31T23:59:59Z");

        AuditEventEntity event = buildEvent(TENANT_ID, "user-1", "Patient/CPID-300");
        event.setCreatedAt(Instant.parse("2025-06-15T12:00:00Z"));

        Page<AuditEventEntity> page = new PageImpl<>(List.of(event));
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        AuditEventQueryParams params = new AuditEventQueryParams(
                TENANT_ID, null, null, null, from, to, 0, 20);

        Page<AuditEventResponse> result = auditQueryService.queryEvents(params);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).createdAt())
                .isEqualTo(Instant.parse("2025-06-15T12:00:00Z"));
    }

    // -----------------------------------------------------------------------
    // findEventById tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("findEventById returns event when tenant matches")
    void findEventById_tenantMatches_returnsEvent() {
        AuditEventEntity event = buildEvent(TENANT_ID, "user-1", "Patient/CPID-400");
        event.setId(EVENT_ID);

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        Optional<AuditEventResponse> result = auditQueryService.findEventById(TENANT_ID, EVENT_ID);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(EVENT_ID);
        assertThat(result.get().tenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("findEventById returns empty when tenant does not match (cross-tenant isolation)")
    void findEventById_tenantMismatch_returnsEmpty() {
        AuditEventEntity event = buildEvent(TENANT_ID, "user-1", "Patient/CPID-400");
        event.setId(EVENT_ID);

        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        Optional<AuditEventResponse> result = auditQueryService.findEventById(OTHER_TENANT, EVENT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findEventById returns empty when event does not exist")
    void findEventById_notFound_returnsEmpty() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        Optional<AuditEventResponse> result = auditQueryService.findEventById(TENANT_ID, EVENT_ID);

        assertThat(result).isEmpty();
    }

    // -----------------------------------------------------------------------
    // getAccessHistory tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getAccessHistory returns redacted access records for given subject")
    void getAccessHistory_returnsAccessRecords() {
        // Given: two events for the same subject
        AuditEventEntity event1 = buildEvent(TENANT_ID, "user-1", "Patient/CPID-500");
        event1.setEventType("DATA_ACCESS");
        event1.setAction("READ");
        event1.setOutcome("SUCCESS");
        event1.setPurposeOfUse("TREATMENT");
        event1.setCreatedAt(Instant.parse("2025-06-15T09:00:00Z"));

        AuditEventEntity event2 = buildEvent(TENANT_ID, "user-2", "Patient/CPID-500");
        event2.setEventType("DATA_ACCESS");
        event2.setAction("UPDATE");
        event2.setOutcome("SUCCESS");
        event2.setPurposeOfUse("EMERGENCY");
        event2.setCreatedAt(Instant.parse("2025-06-15T10:00:00Z"));

        Page<AuditEventEntity> page = new PageImpl<>(List.of(event1, event2));
        when(eventRepository.findByTenantIdAndSubjectRef(
                eq(TENANT_ID), eq("Patient/CPID-500"), any(Pageable.class)))
                .thenReturn(page);

        // When
        Page<AccessHistoryEntry> result = auditQueryService.getAccessHistory(
                TENANT_ID, "Patient/CPID-500", 0, 20);

        // Then: entries contain only redacted fields
        assertThat(result.getContent()).hasSize(2);

        AccessHistoryEntry entry1 = result.getContent().get(0);
        assertThat(entry1.eventType()).isEqualTo("DATA_ACCESS");
        assertThat(entry1.actorType()).isEqualTo("PRACTITIONER");
        assertThat(entry1.action()).isEqualTo("READ");
        assertThat(entry1.outcome()).isEqualTo("SUCCESS");
        assertThat(entry1.purposeOfUse()).isEqualTo("TREATMENT");
        assertThat(entry1.timestamp()).isEqualTo(Instant.parse("2025-06-15T09:00:00Z"));

        AccessHistoryEntry entry2 = result.getContent().get(1);
        assertThat(entry2.action()).isEqualTo("UPDATE");
        assertThat(entry2.purposeOfUse()).isEqualTo("EMERGENCY");
    }

    @Test
    @DisplayName("getAccessHistory caps page size at 100")
    void getAccessHistory_capsPageSize() {
        Page<AuditEventEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(eventRepository.findByTenantIdAndSubjectRef(
                eq(TENANT_ID), eq("Patient/CPID-600"), any(Pageable.class)))
                .thenReturn(emptyPage);

        auditQueryService.getAccessHistory(TENANT_ID, "Patient/CPID-600", 0, 500);

        verify(eventRepository).findByTenantIdAndSubjectRef(
                eq(TENANT_ID), eq("Patient/CPID-600"), pageableCaptor.capture());

        Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getPageSize()).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("getAccessHistory sorts by createdAt descending")
    void getAccessHistory_sortsByCreatedAtDesc() {
        Page<AuditEventEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(eventRepository.findByTenantIdAndSubjectRef(
                eq(TENANT_ID), eq("Patient/CPID-700"), any(Pageable.class)))
                .thenReturn(emptyPage);

        auditQueryService.getAccessHistory(TENANT_ID, "Patient/CPID-700", 0, 20);

        verify(eventRepository).findByTenantIdAndSubjectRef(
                eq(TENANT_ID), eq("Patient/CPID-700"), pageableCaptor.capture());

        Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(captured.getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("getAccessHistory returns empty page when no records exist")
    void getAccessHistory_noRecords_returnsEmptyPage() {
        Page<AuditEventEntity> emptyPage = new PageImpl<>(Collections.emptyList());
        when(eventRepository.findByTenantIdAndSubjectRef(
                eq(TENANT_ID), eq("Patient/CPID-NONE"), any(Pageable.class)))
                .thenReturn(emptyPage);

        Page<AccessHistoryEntry> result = auditQueryService.getAccessHistory(
                TENANT_ID, "Patient/CPID-NONE", 0, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // -----------------------------------------------------------------------
    // DTO mapping tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("toResponse maps all entity fields to the response DTO")
    @SuppressWarnings("unchecked")
    void queryEvents_mapsAllFieldsToResponse() {
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2025-06-15T14:30:00Z");

        AuditEventEntity event = new AuditEventEntity();
        event.setId(eventId);
        event.setTenantId(TENANT_ID);
        event.setEventType("CONSENT_CHANGE");
        event.setActorId("nurse-42");
        event.setActorType("NURSE");
        event.setSubjectRef("Patient/CPID-999");
        event.setResourceType("Consent");
        event.setResourceId("consent-001");
        event.setAction("CREATE");
        event.setOutcome("SUCCESS");
        event.setPurposeOfUse("TREATMENT");
        event.setFacilityId(FACILITY_ID);
        event.setCorrelationId(correlationId);
        event.setDetail("{\"consent\":\"granted\"}");
        event.setPreviousHash("prev123");
        event.setEntryHash("entry456");
        event.setSequenceNumber(7L);
        event.setCreatedAt(createdAt);

        Page<AuditEventEntity> page = new PageImpl<>(List.of(event));
        when(eventRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        AuditEventQueryParams params = new AuditEventQueryParams(
                TENANT_ID, null, null, null, null, null, 0, 20);

        Page<AuditEventResponse> result = auditQueryService.queryEvents(params);

        assertThat(result.getContent()).hasSize(1);
        AuditEventResponse r = result.getContent().get(0);

        assertThat(r.id()).isEqualTo(eventId);
        assertThat(r.tenantId()).isEqualTo(TENANT_ID);
        assertThat(r.eventType()).isEqualTo("CONSENT_CHANGE");
        assertThat(r.actorId()).isEqualTo("nurse-42");
        assertThat(r.actorType()).isEqualTo("NURSE");
        assertThat(r.subjectRef()).isEqualTo("Patient/CPID-999");
        assertThat(r.resourceType()).isEqualTo("Consent");
        assertThat(r.resourceId()).isEqualTo("consent-001");
        assertThat(r.action()).isEqualTo("CREATE");
        assertThat(r.outcome()).isEqualTo("SUCCESS");
        assertThat(r.purposeOfUse()).isEqualTo("TREATMENT");
        assertThat(r.facilityId()).isEqualTo(FACILITY_ID);
        assertThat(r.correlationId()).isEqualTo(correlationId);
        assertThat(r.detail()).isEqualTo("{\"consent\":\"granted\"}");
        assertThat(r.previousHash()).isEqualTo("prev123");
        assertThat(r.entryHash()).isEqualTo("entry456");
        assertThat(r.sequenceNumber()).isEqualTo(7L);
        assertThat(r.createdAt()).isEqualTo(createdAt);
    }
}
