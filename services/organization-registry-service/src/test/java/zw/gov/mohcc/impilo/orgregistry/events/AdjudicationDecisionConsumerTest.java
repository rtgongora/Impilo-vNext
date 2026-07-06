package zw.gov.mohcc.impilo.orgregistry.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import zw.gov.mohcc.impilo.orgregistry.core.ClaimSubmissionService;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdjudicationDecisionConsumerTest {

    @Mock
    ClaimSubmissionService claimSubmissionService;
    @Mock
    Acknowledgment acknowledgment;

    AdjudicationDecisionConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AdjudicationDecisionConsumer(claimSubmissionService, new ObjectMapper());
    }

    @Test
    void routesDecisionRecordedEventToResolution_mappingByWorkflowInstanceId() {
        UUID tenant = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String envelope = """
            {
              "eventType": "impilo.governance.adjudication.decision.recorded",
              "tenantId": "%s",
              "correlationId": "corr-1",
              "payload": {
                "decisionId": "DEC-1",
                "subjectType": "FACILITY",
                "subjectRef": "claim-xyz",
                "decision": "APPROVED",
                "workflowInstanceId": "%s"
              }
            }
            """.formatted(tenant, instanceId);
        when(claimSubmissionService.resolveFromDecision(
                eq(tenant), eq(instanceId), eq("claim-xyz"), eq("APPROVED"), eq("DEC-1"), eq("corr-1")))
                .thenReturn(Optional.empty());

        consumer.onGovernanceEvent(envelope, acknowledgment);

        verify(claimSubmissionService).resolveFromDecision(
                eq(tenant), eq(instanceId), eq("claim-xyz"), eq("APPROVED"), eq("DEC-1"), eq("corr-1"));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void ignoresUnrelatedGovernanceEvents() {
        String envelope = """
            {"eventType":"impilo.governance.something.else","tenantId":"%s","payload":{}}
            """.formatted(UUID.randomUUID());

        consumer.onGovernanceEvent(envelope, acknowledgment);

        verify(claimSubmissionService, never()).resolveFromDecision(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void malformedEventIsAckedAndDroppedNotCrashed() {
        consumer.onGovernanceEvent("{not-json", acknowledgment);

        verify(claimSubmissionService, never()).resolveFromDecision(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(acknowledgment).acknowledge();
    }
}
