package zw.gov.mohcc.impilo.governance.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.governance.persistence.WgvEmploymentConflictCaseEntity;
import zw.gov.mohcc.impilo.governance.persistence.WgvEmploymentConflictCaseRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmploymentConflictCaseServiceTest {

    @Mock WgvEmploymentConflictCaseRepository repository;
    @Mock WgvWorkflowServiceClient workflowServiceClient;

    private static final UUID TENANT = UUID.randomUUID();

    private EmploymentConflictCaseService service() {
        return new EmploymentConflictCaseService(repository, workflowServiceClient);
    }

    @Test
    void openFromMatchOpensCaseAndStartsAdjudicationWhenEnabled() {
        when(repository.findByTenantIdAndEcNumberAndStatusIn(eq(TENANT), anyString(), anyList()))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UUID instance = UUID.randomUUID();
        when(workflowServiceClient.startAdjudication(eq(TENANT), anyString(), any(), anyString(), any()))
                .thenReturn(Optional.of(instance));

        Optional<WgvEmploymentConflictCaseEntity> out = service().openFromMatch(
                TENANT, "ec-100001", "hid-1", Map.of("conflictingRecordCount", 2), "corr-1");

        assertTrue(out.isPresent());
        WgvEmploymentConflictCaseEntity e = out.get();
        assertEquals("EC-100001", e.getEcNumber());     // canonicalised (uppercased, whitespace stripped; hyphen kept)
        assertEquals("hid-1", e.getSubjectRef());
        assertEquals(2, e.getConflictingRecordCount());
        assertEquals("UNDER_REVIEW", e.getStatus());
        assertEquals(instance, e.getWorkflowInstanceId());
    }

    @Test
    void openFromMatchLeavesCaseOpenWhenAdjudicationDisabled() {
        when(repository.findByTenantIdAndEcNumberAndStatusIn(eq(TENANT), anyString(), anyList()))
                .thenReturn(List.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(workflowServiceClient.startAdjudication(eq(TENANT), anyString(), any(), anyString(), any()))
                .thenReturn(Optional.empty());

        WgvEmploymentConflictCaseEntity e = service().openFromMatch(
                TENANT, "EC100002", null, Map.of(), null).orElseThrow();

        assertEquals("OPEN", e.getStatus());
        assertNull(e.getWorkflowInstanceId());
        assertEquals("EC100002", e.getSubjectRef()); // no health id -> EC is the subjectRef
    }

    @Test
    void openFromMatchIsIdempotentPerEc() {
        when(repository.findByTenantIdAndEcNumberAndStatusIn(eq(TENANT), anyString(), anyList()))
                .thenReturn(List.of(new WgvEmploymentConflictCaseEntity()));

        Optional<WgvEmploymentConflictCaseEntity> out = service().openFromMatch(
                TENANT, "EC100001", "hid-1", Map.of(), null);

        assertTrue(out.isEmpty());
        verify(repository, never()).save(any());
        verify(workflowServiceClient, never()).startAdjudication(any(), anyString(), any(), anyString(), any());
    }

    @Test
    void resolveApprovedMarksCaseResolvedApproved() {
        WgvEmploymentConflictCaseEntity existing = new WgvEmploymentConflictCaseEntity();
        existing.setStatus("UNDER_REVIEW");
        UUID instance = UUID.randomUUID();
        when(repository.findByWorkflowInstanceId(instance)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().resolveFromDecision(TENANT, instance, "hid-1", "APPROVED", "dec-1", null);

        assertEquals("RESOLVED_APPROVED", existing.getStatus());
        assertEquals("dec-1", existing.getAdjudicationRef());
    }

    @Test
    void resolveDeniedMarksCaseResolvedRejected() {
        WgvEmploymentConflictCaseEntity existing = new WgvEmploymentConflictCaseEntity();
        existing.setStatus("UNDER_REVIEW");
        when(repository.findByWorkflowInstanceId(any())).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().resolveFromDecision(TENANT, UUID.randomUUID(), "hid-1", "DENIED", "dec-2", null);

        assertEquals("RESOLVED_REJECTED", existing.getStatus());
    }

    @Test
    void resolveConditionalDoesNotAutoResolve() {
        WgvEmploymentConflictCaseEntity existing = new WgvEmploymentConflictCaseEntity();
        existing.setStatus("UNDER_REVIEW");
        when(repository.findByWorkflowInstanceId(any())).thenReturn(Optional.of(existing));

        service().resolveFromDecision(TENANT, UUID.randomUUID(), "hid-1", "CONDITIONAL", "dec-3", null);

        assertEquals("UNDER_REVIEW", existing.getStatus());
        verify(repository, never()).save(any());
    }

    @Test
    void resolveUnknownCaseIsIgnored() {
        when(repository.findByWorkflowInstanceId(any())).thenReturn(Optional.empty());
        when(repository.findByTenantIdAndSubjectRefAndStatusIn(eq(TENANT), anyString(), anyList()))
                .thenReturn(List.of());

        service().resolveFromDecision(TENANT, UUID.randomUUID(), "hid-x", "APPROVED", "dec-4", null);

        verify(repository, never()).save(any());
    }
}
