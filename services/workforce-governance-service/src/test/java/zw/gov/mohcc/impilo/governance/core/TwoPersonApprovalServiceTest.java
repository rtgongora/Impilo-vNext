package zw.gov.mohcc.impilo.governance.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.governance.persistence.AccessRequestEntity;
import zw.gov.mohcc.impilo.governance.persistence.AccessRequestRepository;
import zw.gov.mohcc.impilo.governance.persistence.PlatformActionApprovalEntity;
import zw.gov.mohcc.impilo.governance.persistence.PlatformActionApprovalRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TwoPersonApprovalServiceTest {

    private static final String TWO_ADMINS =
            "[\"PLATFORM_ORIGIN_ADMINISTRATOR\",\"PLATFORM_ORIGIN_ADMINISTRATOR\"]";

    @Mock private PlatformActionApprovalRepository approvalRepository;
    @Mock private AccessRequestRepository accessRequestRepository;
    @Mock private GovernanceEventService governanceEventService;

    private TwoPersonApprovalService service;
    private AccessRequestService accessRequestService;
    private final List<PlatformActionApprovalEntity> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        service = new TwoPersonApprovalService(approvalRepository, objectMapper);
        accessRequestService = new AccessRequestService(
                accessRequestRepository, governanceEventService, objectMapper, service);

        stored.clear();
        when(approvalRepository.save(any())).thenAnswer(i -> {
            PlatformActionApprovalEntity e = i.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            stored.add(e);
            return e;
        });
        when(approvalRepository.findByAccessRequestIdOrderByDecidedAtAsc(any()))
                .thenAnswer(i -> stored.stream()
                        .filter(a -> a.getAccessRequestId().equals(i.getArgument(0)))
                        .toList());
        when(approvalRepository.existsByAccessRequestIdAndApproverUserId(any(), any()))
                .thenAnswer(i -> stored.stream().anyMatch(a ->
                        a.getAccessRequestId().equals(i.getArgument(0))
                        && a.getApproverUserId().equals(i.getArgument(1))));
        when(accessRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private AccessRequestEntity request(String approvalsRequiredJson) {
        AccessRequestEntity entity = new AccessRequestEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(UUID.randomUUID());
        entity.setRequestType("PLATFORM_ACTION");
        entity.setStatus("PENDING");
        entity.setRequesterId("initiator-1");
        entity.setApprovalsRequired(approvalsRequiredJson);
        entity.initTimestamps();
        when(accessRequestRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        return entity;
    }

    // ── Legacy path: null/empty approvals_required ──

    @Test
    void nullApprovalsRequired_keepsLegacySingleApproverPath() {
        AccessRequestEntity entity = request(null);
        assertFalse(service.requiresMultiPartyApproval(entity));
        assertTrue(service.isSatisfied(entity));
        // Transition to APPROVED succeeds with zero recorded approvals.
        AccessRequestEntity out = accessRequestService.transition(
                entity.getId(), "APPROVED", "single approver", "approver-legacy");
        assertEquals("APPROVED", out.getStatus());
    }

    @Test
    void emptyApprovalsRequired_keepsLegacySingleApproverPath() {
        AccessRequestEntity entity = request("[]");
        assertFalse(service.requiresMultiPartyApproval(entity));
        assertTrue(service.isSatisfied(entity));
        assertEquals("APPROVED", accessRequestService.transition(
                entity.getId(), "APPROVED", null, "approver-legacy").getStatus());
    }

    // ── Two-person rule ──

    @Test
    void twoDistinctApproversSatisfyTwoPersonRule() {
        AccessRequestEntity entity = request(TWO_ADMINS);
        assertTrue(service.requiresMultiPartyApproval(entity));

        service.recordApproval(entity, "approver-1", "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", null);
        assertFalse(service.isSatisfied(entity), "one approval must not satisfy a 2-of-2 rule");
        assertThrows(IllegalStateException.class,
                () -> accessRequestService.transition(entity.getId(), "APPROVED", null, "approver-1"));

        service.recordApproval(entity, "approver-2", "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", null);
        assertTrue(service.isSatisfied(entity));
        assertEquals(2, service.approvalCount(entity));
        assertEquals("APPROVED", accessRequestService.transition(
                entity.getId(), "APPROVED", null, "approver-2").getStatus());
    }

    @Test
    void initiatorCannotApproveOwnPlatformAction() {
        AccessRequestEntity entity = request(TWO_ADMINS);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.recordApproval(entity, "initiator-1",
                        "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", null));
        assertTrue(ex.getMessage().toLowerCase().contains("initiator"));
        verify(approvalRepository, never()).save(any());
    }

    @Test
    void duplicateApproverIsRejected() {
        AccessRequestEntity entity = request(TWO_ADMINS);
        service.recordApproval(entity, "approver-1", "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", null);
        assertThrows(IllegalArgumentException.class,
                () -> service.recordApproval(entity, "approver-1",
                        "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", "second attempt"));
        assertFalse(service.isSatisfied(entity), "a duplicate approver must never count twice");
    }

    @Test
    void approverRoleMustSatisfyRequirement() {
        AccessRequestEntity entity = request(TWO_ADMINS);
        service.recordApproval(entity, "approver-1", "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", null);
        service.recordApproval(entity, "approver-2", "FACILITY_ADMIN", "APPROVE", null);
        assertFalse(service.isSatisfied(entity),
                "an approver without the required role must not satisfy the rule");
    }

    @Test
    void rejectDecisionDoesNotCountTowardsApprovals() {
        AccessRequestEntity entity = request(TWO_ADMINS);
        service.recordApproval(entity, "approver-1", "PLATFORM_ORIGIN_ADMINISTRATOR", "APPROVE", null);
        service.recordApproval(entity, "approver-2", "PLATFORM_ORIGIN_ADMINISTRATOR", "REJECT", "no");
        assertFalse(service.isSatisfied(entity));
    }

    @Test
    void nonApprovedTransitionsRemainUnaffected() {
        AccessRequestEntity entity = request(TWO_ADMINS);
        // e.g. REJECTED does not demand the two-person rule.
        assertEquals("REJECTED", accessRequestService.transition(
                entity.getId(), "REJECTED", "not appropriate", "approver-1").getStatus());
    }
}
