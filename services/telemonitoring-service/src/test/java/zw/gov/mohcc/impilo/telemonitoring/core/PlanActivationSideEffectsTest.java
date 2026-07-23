package zw.gov.mohcc.impilo.telemonitoring.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.telemonitoring.core.MonitoringPlanService.CreatePlanCommand;
import zw.gov.mohcc.impilo.telemonitoring.domain.ConsentStatus;
import zw.gov.mohcc.impilo.telemonitoring.domain.PlanStatus;
import zw.gov.mohcc.impilo.telemonitoring.integration.PctTaskClient;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OF-B21 activation side-effect matrix (§14.2 approval semantics):
 * consent gate fails closed ONLY on explicit refusal; absent consent is honest UNVERIFIED;
 * a CHW binding on the care team raises the initial PCT task (generic lane) with the
 * durable {@code telemonitoring.plan.task_requested.v1} outbox seam.
 */
@SpringBootTest
@ActiveProfiles("test")
class PlanActivationSideEffectsTest {

    @Autowired private MonitoringPlanService planService;
    @Autowired private MonitoringProgrammeRepository programmeRepository;
    @Autowired private EventOutboxRepository outboxRepository;

    @MockBean private PctTaskClient pctTaskClient;

    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void seedProgramme() {
        if (programmeRepository.findByCode("HYPERTENSION").isEmpty()) {
            MonitoringProgrammeEntity p = new MonitoringProgrammeEntity();
            p.setCode("HYPERTENSION");
            p.setName("Hypertension");
            p.setDefaultThresholds("{\"systolic_bp\":{\"greenMax\":140}}");
            programmeRepository.save(p);
        }
        when(pctTaskClient.createTask(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);
    }

    private CreatePlanCommand cmd(String careTeamJson, String consentReference, String consentStatus) {
        return new CreatePlanCommand(tenant, "CPID-" + UUID.randomUUID(), "HYPERTENSION", null,
                "Essential hypertension", "CHW_ADMINISTERED", "WEEKLY", careTeamJson, null,
                null, null, "clinician-a", consentReference, consentStatus);
    }

    // ── (b) Consent gate: fail-closed on explicit refusal only ──

    @Test
    void explicitRefusalBlocksActivation() {
        MonitoringPlanEntity plan = planService.createDraft(cmd(null, "MVUMO-REF-1", "REFUSED"), true);
        assertThatThrownBy(() -> planService.approve(plan.getId(), "clinician-b"))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("REFUSED")
                .hasMessageContaining("MVUMO-REF-1");
        // Fail-closed means nothing moved: still DRAFT, no activation event.
        assertThat(planService.getPlan(plan.getId()).getStatus()).isEqualTo(PlanStatus.DRAFT);
        assertThat(events(plan)).containsExactly("telemonitoring.plan.created.v1");
    }

    @Test
    void revokedConsentPointerBlocksActivation() {
        MonitoringPlanEntity plan = planService.createDraft(cmd(null, "MVUMO-REF-2", "GRANTED"), true);
        planService.recordConsentPointer(plan.getId(), "MVUMO-REF-2", "REVOKED", "mvumo-sync");
        assertThatThrownBy(() -> planService.approve(plan.getId(), "clinician-b"))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("REVOKED");
    }

    @Test
    void absentConsentIsHonestUnverifiedAndActivationProceeds() {
        MonitoringPlanEntity plan = planService.createDraft(cmd(null, null, null), true);
        assertThat(plan.getConsentStatus()).isEqualTo(ConsentStatus.UNVERIFIED);

        MonitoringPlanEntity active = planService.approve(plan.getId(), "clinician-b");
        assertThat(active.getStatus()).isEqualTo(PlanStatus.ACTIVE);

        // The honest state is RECORDED on the activation event, never upgraded.
        EventOutboxEntity activated = eventOfType(plan, "telemonitoring.plan.activated.v1");
        assertThat(activated.getPayloadJson()).contains("\"consentStatus\":\"UNVERIFIED\"");
    }

    @Test
    void grantedConsentActivatesAndIsRecorded() {
        MonitoringPlanEntity plan = planService.createDraft(cmd(null, "MVUMO-REF-3", "GRANTED"), true);
        MonitoringPlanEntity active = planService.approve(plan.getId(), "clinician-b");
        assertThat(active.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        EventOutboxEntity activated = eventOfType(plan, "telemonitoring.plan.activated.v1");
        assertThat(activated.getPayloadJson()).contains("\"consentStatus\":\"GRANTED\"");
        assertThat(activated.getPayloadJson()).contains("MVUMO-REF-3");
    }

    @Test
    void unknownConsentStatusIsRejectedNotGuessed() {
        assertThatThrownBy(() -> planService.createDraft(cmd(null, null, "MAYBE"), true))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("MAYBE");
    }

    @Test
    void consentPointerUpdateEmitsEventAndIsRefusedOnTerminalPlans() {
        MonitoringPlanEntity plan = planService.createDraft(cmd(null, null, null), true);
        planService.recordConsentPointer(plan.getId(), "MVUMO-REF-4", "PENDING", "mvumo-sync");
        assertThat(events(plan)).contains("telemonitoring.plan.consent_updated.v1");

        planService.cancel(plan.getId(), "abandoned", "clinician-a");
        assertThatThrownBy(() -> planService.recordConsentPointer(
                plan.getId(), "MVUMO-REF-4", "GRANTED", "mvumo-sync"))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("CANCELLED");
    }

    // ── (a) CHW task seam: PCT generic lane + durable task_requested event ──

    @Test
    void chwBindingRaisesPctTaskAndDurableSeamEvent() {
        UUID workspace = UUID.randomUUID();
        MonitoringPlanEntity plan = planService.createDraft(cmd(
                "{\"chwId\":\"chw-123\",\"chwWorkspaceId\":\"" + workspace + "\",\"escalationFacilityId\":\"fac-9\"}",
                "MVUMO-REF-5", "GRANTED"), true);
        planService.approve(plan.getId(), "clinician-b");

        verify(pctTaskClient).createTask(eq(tenant), eq("MONITORING_ONBOARDING_VISIT"),
                eq("chw-123"), eq("CHW"), eq(workspace), isNull(), any());

        EventOutboxEntity seam = eventOfType(plan, "telemonitoring.plan.task_requested.v1");
        assertThat(seam.getPayloadJson()).contains("\"assigneeId\":\"chw-123\"");
        assertThat(seam.getPayloadJson()).contains("\"dispatchedToPct\":true");
    }

    @Test
    void failedPctPushStillLeavesTheDurableSeamEvent() {
        when(pctTaskClient.createTask(any(), any(), any(), any(), any(), any(), any())).thenReturn(false);
        MonitoringPlanEntity plan = planService.createDraft(
                cmd("{\"chwId\":\"chw-456\"}", null, "GRANTED"), true);
        planService.approve(plan.getId(), "clinician-b");

        // Activation survived the degraded PCT; the outbox row is the honest record.
        assertThat(planService.getPlan(plan.getId()).getStatus()).isEqualTo(PlanStatus.ACTIVE);
        EventOutboxEntity seam = eventOfType(plan, "telemonitoring.plan.task_requested.v1");
        assertThat(seam.getPayloadJson()).contains("\"dispatchedToPct\":false");
    }

    @Test
    void noChwBindingMeansNoTaskAndNoSeamEvent() {
        MonitoringPlanEntity plan = planService.createDraft(cmd(
                "{\"escalationFacilityId\":\"fac-9\",\"responsibleTeam\":\"chronic-care\"}",
                null, "GRANTED"), true);
        planService.approve(plan.getId(), "clinician-b");

        verify(pctTaskClient, never()).createTask(any(), any(), any(), any(), any(), any(), any());
        assertThat(events(plan)).doesNotContain("telemonitoring.plan.task_requested.v1");
    }

    private List<String> events(MonitoringPlanEntity plan) {
        return outboxRepository.findByAggregateIdOrderByIdAsc(plan.getId().toString()).stream()
                .map(EventOutboxEntity::getEventType)
                .toList();
    }

    private EventOutboxEntity eventOfType(MonitoringPlanEntity plan, String eventType) {
        return outboxRepository.findByAggregateIdOrderByIdAsc(plan.getId().toString()).stream()
                .filter(e -> eventType.equals(e.getEventType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected outbox event " + eventType));
    }
}
