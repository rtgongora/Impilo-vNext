package zw.gov.mohcc.impilo.governance.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.governance.core.AdjudicationDecisionService.RecordDecisionCommand;
import zw.gov.mohcc.impilo.governance.persistence.AdjudicationDecisionEntity;
import zw.gov.mohcc.impilo.governance.persistence.AdjudicationDecisionRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdjudicationDecisionServiceTest {

    @Mock private AdjudicationDecisionRepository repository;
    @Mock private GovernanceEventService eventService;
    @Mock private EmploymentConflictCaseService employmentConflictCaseService;

    private AdjudicationDecisionService service;
    private final UUID tenantId = UUID.randomUUID();
    private final Instant now = Instant.now();

    @BeforeEach
    void setUp() {
        service = new AdjudicationDecisionService(repository, eventService, employmentConflictCaseService);
        when(repository.save(any())).thenAnswer(i -> {
            AdjudicationDecisionEntity e = i.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });
    }

    private RecordDecisionCommand cmd(String subjectType, String subjectRef, String decision, String reason,
                                      Instant effectiveAt, Instant expiresAt) {
        return new RecordDecisionCommand(null, subjectType, subjectRef, decision, reason,
                "authority-1", "REGISTRAR", effectiveAt, expiresAt, null, null, null, null);
    }

    // ── Reason is mandatory ──

    @Test
    void record_rejectsNullReason() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.record(tenantId, "actor-1",
                        cmd("PROVIDER", "prov-1", "APPROVED", null, null, null), "c"));
        assertTrue(ex.getMessage().toLowerCase().contains("reason"));
        verify(repository, never()).save(any());
        verify(eventService, never()).enqueue(any(), any(), any(), any(), any(), any());
    }

    @Test
    void record_rejectsBlankReason() {
        assertThrows(IllegalArgumentException.class,
                () -> service.record(tenantId, "actor-1",
                        cmd("PROVIDER", "prov-1", "APPROVED", "   ", null, null), "c"));
        verify(repository, never()).save(any());
    }

    // ── Enumeration and window validation ──

    @Test
    void record_rejectsUnknownDecision() {
        assertThrows(IllegalArgumentException.class,
                () -> service.record(tenantId, "actor-1",
                        cmd("PROVIDER", "prov-1", "MAYBE", "reason", null, null), "c"));
        verify(repository, never()).save(any());
    }

    @Test
    void record_rejectsUnknownSubjectType() {
        assertThrows(IllegalArgumentException.class,
                () -> service.record(tenantId, "actor-1",
                        cmd("DEVICE", "dev-1", "APPROVED", "reason", null, null), "c"));
        verify(repository, never()).save(any());
    }

    @Test
    void record_rejectsBlankSubjectRef() {
        assertThrows(IllegalArgumentException.class,
                () -> service.record(tenantId, "actor-1",
                        cmd("IDENTITY", " ", "APPROVED", "reason", null, null), "c"));
        verify(repository, never()).save(any());
    }

    @Test
    void record_rejectsExpiryNotAfterEffective() {
        assertThrows(IllegalArgumentException.class,
                () -> service.record(tenantId, "actor-1",
                        cmd("FACILITY", "fac-1", "CONDITIONAL", "reason", now, now.minusSeconds(60)), "c"));
        verify(repository, never()).save(any());
    }

    // ── Recording ──

    @Test
    void record_persistsDecisionAndEmitsEvent() {
        AdjudicationDecisionEntity e = service.record(tenantId, "actor-1",
                new RecordDecisionCommand(UUID.randomUUID(), "provider", "prov-1", "conditional",
                        "Licence pending council confirmation", "authority-1", "REGISTRAR",
                        now, now.plus(30, ChronoUnit.DAYS),
                        Map.of("prescribe", false), Map.of("supervision", "required"),
                        List.of("doc://evidence/1"), UUID.randomUUID()), "corr-1");

        assertEquals("PROVIDER", e.getSubjectType());
        assertEquals("CONDITIONAL", e.getDecision());
        assertEquals("prov-1", e.getSubjectRef());
        assertEquals(tenantId, e.getTenantId());
        assertEquals("actor-1", e.getCreatedBy());
        assertNotNull(e.getWorkflowInstanceId());
        verify(eventService).enqueue(eq("ADJUDICATION_DECISION"), any(),
                eq(AdjudicationDecisionService.EVENT_DECISION_RECORDED), any(), eq(tenantId), eq("corr-1"));
    }

    @Test
    void record_defaultsEffectiveAtToNow() {
        AdjudicationDecisionEntity e = service.record(tenantId, "actor-1",
                cmd("IDENTITY", "hid-1", "APPROVED", "verified", null, null), "c");
        assertNotNull(e.getEffectiveAt());
        assertFalse(e.getEffectiveAt().isAfter(Instant.now()));
    }

    // ── Append-only supersede ──

    @Test
    void record_supersedeInsertsNewRowNeverMutatesExisting() {
        AdjudicationDecisionEntity first = service.record(tenantId, "actor-1",
                cmd("PROVIDER", "prov-1", "DENIED", "insufficient evidence", now.minusSeconds(3600), null), "c1");
        AdjudicationDecisionEntity second = service.record(tenantId, "actor-2",
                cmd("PROVIDER", "prov-1", "APPROVED", "new evidence accepted", now, null), "c2");

        ArgumentCaptor<AdjudicationDecisionEntity> captor =
                ArgumentCaptor.forClass(AdjudicationDecisionEntity.class);
        verify(repository, times(2)).save(captor.capture());
        // Two distinct inserts — supersede never rewrites the first row.
        assertNotSame(captor.getAllValues().get(0), captor.getAllValues().get(1));
        assertNotEquals(first.getId(), second.getId());
        assertEquals("DENIED", first.getDecision());
        assertEquals("APPROVED", second.getDecision());
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteById(any());
    }

    // ── Latest-effective resolution + history ──

    @Test
    void latestEffective_respectsEffectiveAndExpiryWindows() {
        AdjudicationDecisionEntity futureRow = decision("CONDITIONAL", now.plus(1, ChronoUnit.DAYS), null);
        AdjudicationDecisionEntity expiredRow = decision("DENIED",
                now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        AdjudicationDecisionEntity openRow = decision("APPROVED", now.minus(3, ChronoUnit.DAYS), null);
        // Repository contract: newest effective_at first.
        when(repository.findByTenantIdAndSubjectTypeAndSubjectRefOrderByEffectiveAtDescCreatedAtDesc(
                tenantId, "PROVIDER", "prov-1"))
                .thenReturn(List.of(futureRow, expiredRow, openRow));

        Optional<AdjudicationDecisionEntity> latest =
                service.latestEffective(tenantId, "PROVIDER", "prov-1", now);

        // Not the not-yet-effective row, not the expired row — the open older row.
        assertTrue(latest.isPresent());
        assertSame(openRow, latest.get());
    }

    @Test
    void latestEffective_prefersNewestEffectiveAmongValid() {
        AdjudicationDecisionEntity newer = decision("CONDITIONAL", now.minus(1, ChronoUnit.HOURS), null);
        AdjudicationDecisionEntity older = decision("APPROVED", now.minus(2, ChronoUnit.DAYS), null);
        when(repository.findByTenantIdAndSubjectTypeAndSubjectRefOrderByEffectiveAtDescCreatedAtDesc(
                tenantId, "FACILITY", "fac-1"))
                .thenReturn(List.of(newer, older));

        Optional<AdjudicationDecisionEntity> latest =
                service.latestEffective(tenantId, "FACILITY", "fac-1", now);
        assertTrue(latest.isPresent());
        assertEquals("CONDITIONAL", latest.get().getDecision());
    }

    @Test
    void latestEffective_emptyWhenNoRowInWindow() {
        AdjudicationDecisionEntity futureRow = decision("APPROVED", now.plus(1, ChronoUnit.DAYS), null);
        when(repository.findByTenantIdAndSubjectTypeAndSubjectRefOrderByEffectiveAtDescCreatedAtDesc(
                tenantId, "IDENTITY", "hid-1"))
                .thenReturn(List.of(futureRow));
        assertTrue(service.latestEffective(tenantId, "IDENTITY", "hid-1", now).isEmpty());
    }

    @Test
    void history_returnsAllRowsIncludingSupersededAndExpired() {
        AdjudicationDecisionEntity newer = decision("APPROVED", now, null);
        AdjudicationDecisionEntity superseded = decision("DENIED",
                now.minus(2, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        when(repository.findByTenantIdAndSubjectTypeAndSubjectRefOrderByEffectiveAtDescCreatedAtDesc(
                tenantId, "ORGANIZATION", "org-1"))
                .thenReturn(List.of(newer, superseded));

        List<AdjudicationDecisionEntity> history = service.history(tenantId, "ORGANIZATION", "org-1");
        assertEquals(2, history.size());
        assertEquals("APPROVED", history.get(0).getDecision());
        assertEquals("DENIED", history.get(1).getDecision());
    }

    @Test
    void history_rejectsUnknownSubjectType() {
        assertThrows(IllegalArgumentException.class,
                () -> service.history(tenantId, "GADGET", "ref-1"));
    }

    private AdjudicationDecisionEntity decision(String outcome, Instant effectiveAt, Instant expiresAt) {
        AdjudicationDecisionEntity e = new AdjudicationDecisionEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setSubjectType("PROVIDER");
        e.setSubjectRef("prov-1");
        e.setDecision(outcome);
        e.setReason("r");
        e.setEffectiveAt(effectiveAt);
        e.setExpiresAt(expiresAt);
        return e;
    }
}
