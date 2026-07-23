package zw.gov.mohcc.impilo.telemonitoring.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.telemonitoring.core.MonitoringPlanService.CreatePlanCommand;
import zw.gov.mohcc.impilo.telemonitoring.domain.PlanStatus;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.ThresholdProfileRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class MonitoringPlanServiceTest {

    @Autowired private MonitoringPlanService planService;
    @Autowired private MonitoringProgrammeRepository programmeRepository;
    @Autowired private ThresholdProfileRepository thresholdRepository;
    @Autowired private EventOutboxRepository outboxRepository;

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
    }

    private CreatePlanCommand cmd(String orosOrderId, String suggestedBy) {
        return new CreatePlanCommand(tenant, "CPID-" + UUID.randomUUID(), "HYPERTENSION", orosOrderId,
                "Essential hypertension", "HOME_SELF_MONITORING", "WEEKLY", null, null,
                suggestedBy, suggestedBy != null ? "v1.2" : null, "clinician-a");
    }

    @Test
    void createDraftSeedsThresholdV1FromProgrammeDefaults() {
        MonitoringPlanEntity plan = planService.createDraft(cmd(null, null), true);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);
        var versions = thresholdRepository.findByPlanIdOrderByVersionDesc(plan.getId());
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getVersion()).isEqualTo(1);
        assertThat(versions.get(0).getParameters()).contains("systolic_bp");
        assertThat(events(plan)).containsExactly("telemonitoring.plan.created.v1");
    }

    @Test
    void strictLaneRejectsUnknownProgramme() {
        CreatePlanCommand bad = new CreatePlanCommand(tenant, "CPID-X", "NOT_A_PROGRAMME", null,
                null, null, null, null, null, null, null, "clinician-a");
        assertThatThrownBy(() -> planService.createDraft(bad, true))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("NOT_A_PROGRAMME");
    }

    @Test
    void approveActivatesThroughPendingApprovalAndRecordsApprover() {
        MonitoringPlanEntity plan = planService.createDraft(cmd(null, null), true);
        MonitoringPlanEntity active = planService.approve(plan.getId(), "clinician-b");
        assertThat(active.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(active.getApprovedBy()).isEqualTo("clinician-b");
        assertThat(active.getApprovedAt()).isNotNull();
        assertThat(active.getStartAt()).isNotNull();
        assertThat(events(plan)).containsExactly(
                "telemonitoring.plan.created.v1", "telemonitoring.plan.activated.v1");
    }

    @Test
    void approvalGateSuggestionCannotSelfActivate() {
        MonitoringPlanEntity suggested = planService.createDraft(cmd(null, "NOMPILO-RISK-MODEL"), true);
        // Creation never yields anything but DRAFT, whatever the provenance.
        assertThat(suggested.getStatus()).isEqualTo(PlanStatus.DRAFT);

        // The suggesting system cannot approve its own suggestion.
        assertThatThrownBy(() -> planService.approve(suggested.getId(), "NOMPILO-RISK-MODEL"))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("self-activate");

        // Approval without an identified approver is rejected.
        assertThatThrownBy(() -> planService.approve(suggested.getId(), " "))
                .isInstanceOf(TelemonitoringDomainException.class);

        // Pre-approval, the only emitted event is the internal created signal —
        // no activation, no patient-facing notification.
        assertThat(events(suggested)).containsExactly("telemonitoring.plan.created.v1");

        // A distinct clinician can approve.
        MonitoringPlanEntity active = planService.approve(suggested.getId(), "clinician-c");
        assertThat(active.getStatus()).isEqualTo(PlanStatus.ACTIVE);
    }

    @Test
    void lifecycleTransitionsAreReasonBoundAndEmitEvents() {
        MonitoringPlanEntity plan = planService.createDraft(cmd(null, null), true);
        planService.approve(plan.getId(), "clinician-b");

        assertThatThrownBy(() -> planService.suspend(plan.getId(), " ", "clinician-b"))
                .isInstanceOf(TelemonitoringDomainException.class);

        planService.suspend(plan.getId(), "Patient admitted to hospital", "clinician-b");
        assertThat(planService.getPlan(plan.getId()).getStatus()).isEqualTo(PlanStatus.SUSPENDED);

        assertThatThrownBy(() -> planService.resume(plan.getId(), "", "clinician-b"))
                .isInstanceOf(TelemonitoringDomainException.class);
        planService.resume(plan.getId(), "Discharged, monitoring resumes", "clinician-b");
        assertThat(planService.getPlan(plan.getId()).getStatus()).isEqualTo(PlanStatus.ACTIVE);

        planService.complete(plan.getId(), "Programme finished", "clinician-b");
        MonitoringPlanEntity done = planService.getPlan(plan.getId());
        assertThat(done.getStatus()).isEqualTo(PlanStatus.COMPLETED);
        assertThat(done.getEndAt()).isNotNull();

        // Terminal: no further transitions.
        assertThatThrownBy(() -> planService.cancel(plan.getId(), "too late", "clinician-b"))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("COMPLETED");

        assertThat(events(plan)).containsExactly(
                "telemonitoring.plan.created.v1",
                "telemonitoring.plan.activated.v1",
                "telemonitoring.plan.suspended.v1",
                "telemonitoring.plan.activated.v1",
                "telemonitoring.plan.completed.v1");
    }

    @Test
    void cancelIsReasonBoundAndTerminal() {
        MonitoringPlanEntity plan = planService.createDraft(cmd(null, null), true);
        assertThatThrownBy(() -> planService.cancel(plan.getId(), null, "clinician-b"))
                .isInstanceOf(TelemonitoringDomainException.class);
        planService.cancel(plan.getId(), "Duplicate enrolment", "clinician-b");
        assertThat(planService.getPlan(plan.getId()).getStatus()).isEqualTo(PlanStatus.CANCELLED);
        assertThatThrownBy(() -> planService.approve(plan.getId(), "clinician-b"))
                .isInstanceOf(TelemonitoringDomainException.class);
    }

    @Test
    void duplicateOrosOrderIsRejected() {
        String orderId = "01TESTORDER" + UUID.randomUUID().toString().substring(0, 8);
        planService.createDraft(cmd(orderId, null), true);
        assertThatThrownBy(() -> planService.createDraft(cmd(orderId, null), true))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining(orderId);
    }

    private List<String> events(MonitoringPlanEntity plan) {
        return outboxRepository.findByAggregateIdOrderByIdAsc(plan.getId().toString()).stream()
                .map(EventOutboxEntity::getEventType)
                .toList();
    }
}
