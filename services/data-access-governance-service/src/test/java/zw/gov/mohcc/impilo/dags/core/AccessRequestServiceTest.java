package zw.gov.mohcc.impilo.dags.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.dags.persistence.entity.AccessDecisionEntity;
import zw.gov.mohcc.impilo.dags.persistence.entity.AccessRequestEntity;
import zw.gov.mohcc.impilo.dags.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.dags.persistence.repository.AccessDecisionRepository;
import zw.gov.mohcc.impilo.dags.persistence.repository.AccessRequestRepository;
import zw.gov.mohcc.impilo.dags.persistence.repository.EventOutboxRepository;

@ExtendWith(MockitoExtension.class)
class AccessRequestServiceTest {

    @Mock private AccessRequestRepository requestRepository;
    @Mock private AccessDecisionRepository decisionRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private EnforcementService enforcementService;
    @Mock private AuditService auditService;

    private AccessRequestService service;

    @BeforeEach
    void setUp() {
        service = new AccessRequestService(
                requestRepository, decisionRepository, outboxRepository,
                enforcementService, auditService);
    }

    @Nested
    @DisplayName("Submit Access Request")
    class Submit {

        @Test
        void createsRequestWithSubmittedStatus() {
            UUID tenantId = UUID.randomUUID();
            String actorId = "user-1";

            AccessRequestEntity saved = new AccessRequestEntity();
            saved.setId(10L);
            saved.setTenantId(tenantId);
            saved.setRequesterId(actorId);
            saved.setResourceType("PATIENT_RECORD");
            saved.setResourceId("patient-123");
            saved.setPurpose("Clinical review");
            saved.setStatus(AccessRequestStatus.SUBMITTED);

            when(requestRepository.save(any(AccessRequestEntity.class))).thenReturn(saved);

            AccessRequestEntity result = service.submit(tenantId, actorId, UUID.randomUUID(),
                    "PATIENT_RECORD", "patient-123", "Clinical review", null);

            assertThat(result.getStatus()).isEqualTo(AccessRequestStatus.SUBMITTED);
            assertThat(result.getRequesterId()).isEqualTo("user-1");
            assertThat(result.getResourceType()).isEqualTo("PATIENT_RECORD");
        }

        @Test
        void publishesAccessRequestedEvent() {
            UUID tenantId = UUID.randomUUID();
            AccessRequestEntity saved = new AccessRequestEntity();
            saved.setId(11L);
            saved.setTenantId(tenantId);
            saved.setRequesterId("user-1");
            saved.setResourceType("LAB");
            saved.setResourceId("lab-1");
            saved.setPurpose("Research");
            saved.setStatus(AccessRequestStatus.SUBMITTED);

            when(requestRepository.save(any())).thenReturn(saved);

            service.submit(tenantId, "user-1", UUID.randomUUID(),
                    "LAB", "lab-1", "Research", null);

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getEventType()).isEqualTo("ACCESS_REQUESTED");
        }
    }

    @Nested
    @DisplayName("Approve Access Request")
    class Approve {

        @Test
        void transitionsToApprovedAndIssuesPermitToken() {
            UUID tenantId = UUID.randomUUID();
            Long requestId = 20L;

            AccessRequestEntity request = new AccessRequestEntity();
            request.setId(requestId);
            request.setTenantId(tenantId);
            request.setRequesterId("user-2");
            request.setResourceType("PATIENT_RECORD");
            request.setStatus(AccessRequestStatus.SUBMITTED);

            when(requestRepository.findByIdAndTenantId(requestId, tenantId))
                    .thenReturn(Optional.of(request));
            when(enforcementService.issuePermitToken(any()))
                    .thenReturn("permit-token:test-token");
            when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AccessRequestEntity result = service.approve(requestId, tenantId, "approver-1",
                    UUID.randomUUID(), "Meets criteria");

            assertThat(result.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);
            assertThat(result.getPermitToken()).isEqualTo("permit-token:test-token");
        }

        @Test
        void createsApprovedDecisionRecord() {
            UUID tenantId = UUID.randomUUID();
            Long requestId = 21L;

            AccessRequestEntity request = new AccessRequestEntity();
            request.setId(requestId);
            request.setTenantId(tenantId);
            request.setRequesterId("user-3");
            request.setResourceType("DATA");
            request.setStatus(AccessRequestStatus.SUBMITTED);

            when(requestRepository.findByIdAndTenantId(requestId, tenantId))
                    .thenReturn(Optional.of(request));
            when(enforcementService.issuePermitToken(any()))
                    .thenReturn("permit-token:abc");
            when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.approve(requestId, tenantId, "approver-1", UUID.randomUUID(), "OK");

            ArgumentCaptor<AccessDecisionEntity> captor =
                    ArgumentCaptor.forClass(AccessDecisionEntity.class);
            verify(decisionRepository).save(captor.capture());

            AccessDecisionEntity decision = captor.getValue();
            assertThat(decision.getDecision()).isEqualTo(DecisionType.APPROVED);
            assertThat(decision.getDecidedBy()).isEqualTo("approver-1");
            assertThat(decision.getReason()).isEqualTo("OK");
        }

        @Test
        void rejectsApprovalOfNonSubmittedRequest() {
            UUID tenantId = UUID.randomUUID();
            Long requestId = 22L;

            AccessRequestEntity request = new AccessRequestEntity();
            request.setId(requestId);
            request.setTenantId(tenantId);
            request.setStatus(AccessRequestStatus.APPROVED);

            when(requestRepository.findByIdAndTenantId(requestId, tenantId))
                    .thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.approve(requestId, tenantId, "approver", UUID.randomUUID(), null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not in SUBMITTED status");
        }

        @Test
        void throwsWhenRequestNotFound() {
            UUID tenantId = UUID.randomUUID();
            when(requestRepository.findByIdAndTenantId(999L, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approve(999L, tenantId, "approver", UUID.randomUUID(), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }
    }

    @Nested
    @DisplayName("Deny Access Request")
    class Deny {

        @Test
        void transitionsToDeniedStatus() {
            UUID tenantId = UUID.randomUUID();
            Long requestId = 30L;

            AccessRequestEntity request = new AccessRequestEntity();
            request.setId(requestId);
            request.setTenantId(tenantId);
            request.setRequesterId("user-4");
            request.setResourceType("PHI");
            request.setStatus(AccessRequestStatus.SUBMITTED);

            when(requestRepository.findByIdAndTenantId(requestId, tenantId))
                    .thenReturn(Optional.of(request));
            when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AccessRequestEntity result = service.deny(requestId, tenantId, "denier-1",
                    UUID.randomUUID(), "Insufficient justification");

            assertThat(result.getStatus()).isEqualTo(AccessRequestStatus.DENIED);
            assertThat(result.getPermitToken()).isNull();
        }

        @Test
        void publishesDeniedEvent() {
            UUID tenantId = UUID.randomUUID();
            Long requestId = 31L;

            AccessRequestEntity request = new AccessRequestEntity();
            request.setId(requestId);
            request.setTenantId(tenantId);
            request.setRequesterId("user-5");
            request.setResourceType("DATA");
            request.setStatus(AccessRequestStatus.SUBMITTED);

            when(requestRepository.findByIdAndTenantId(requestId, tenantId))
                    .thenReturn(Optional.of(request));
            when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.deny(requestId, tenantId, "denier-1", UUID.randomUUID(), "Denied");

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getEventType()).isEqualTo("ACCESS_DENIED");
        }

        @Test
        void rejectsDenialOfNonSubmittedRequest() {
            UUID tenantId = UUID.randomUUID();
            Long requestId = 32L;

            AccessRequestEntity request = new AccessRequestEntity();
            request.setId(requestId);
            request.setTenantId(tenantId);
            request.setStatus(AccessRequestStatus.DENIED);

            when(requestRepository.findByIdAndTenantId(requestId, tenantId))
                    .thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.deny(requestId, tenantId, "denier", UUID.randomUUID(), null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not in SUBMITTED status");
        }
    }
}
