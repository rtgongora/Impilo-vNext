package zw.gov.mohcc.impilo.telemonitoring.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.telemonitoring.core.MonitoringPlanService.AmendThresholdsCommand;
import zw.gov.mohcc.impilo.telemonitoring.core.MonitoringPlanService.CreatePlanCommand;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringPlanEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.ThresholdProfileEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.ThresholdProfileRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ThresholdProfileVersioningTest {

    @Autowired private MonitoringPlanService planService;
    @Autowired private MonitoringProgrammeRepository programmeRepository;
    @Autowired private ThresholdProfileRepository thresholdRepository;

    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void seedProgramme() {
        if (programmeRepository.findByCode("DIABETES").isEmpty()) {
            MonitoringProgrammeEntity p = new MonitoringProgrammeEntity();
            p.setCode("DIABETES");
            p.setName("Diabetes");
            p.setDefaultThresholds("{\"glucose_fasting\":{\"greenMax\":7.0}}");
            programmeRepository.save(p);
        }
    }

    private MonitoringPlanEntity newPlan() {
        return planService.createDraft(new CreatePlanCommand(tenant, "CPID-" + UUID.randomUUID(),
                "DIABETES", null, null, null, null, null, null, null, null, "clinician-a"), true);
    }

    @Test
    void amendmentCreatesImmutableNewVersionWithSupersedesChain() {
        MonitoringPlanEntity plan = newPlan();
        List<ThresholdProfileEntity> before = thresholdRepository.findByPlanIdOrderByVersionDesc(plan.getId());
        assertThat(before).hasSize(1);
        ThresholdProfileEntity v1 = before.get(0);
        String v1Parameters = v1.getParameters();

        ThresholdProfileEntity v2 = planService.amendThresholds(plan.getId(), new AmendThresholdsCommand(
                "{\"glucose_fasting\":{\"greenMax\":8.5}}", "Pregnancy-adjusted range", "clinician-b", 1));

        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(v2.getSupersedes()).isEqualTo(v1.getId());
        assertThat(v2.getCreatedBy()).isEqualTo("clinician-b");

        // Immutability: v1 row untouched.
        ThresholdProfileEntity v1After = thresholdRepository.findById(v1.getId()).orElseThrow();
        assertThat(v1After.getParameters()).isEqualTo(v1Parameters);
        assertThat(v1After.getVersion()).isEqualTo(1);

        List<ThresholdProfileEntity> versions = planService.listThresholdVersions(plan.getId());
        assertThat(versions).extracting(ThresholdProfileEntity::getVersion).containsExactly(2, 1);
    }

    @Test
    void amendmentRequiresClinicianAndReason() {
        MonitoringPlanEntity plan = newPlan();
        assertThatThrownBy(() -> planService.amendThresholds(plan.getId(),
                new AmendThresholdsCommand("{}", "reason", " ", null)))
                .isInstanceOf(TelemonitoringDomainException.class);
        assertThatThrownBy(() -> planService.amendThresholds(plan.getId(),
                new AmendThresholdsCommand("{}", " ", "clinician-b", null)))
                .isInstanceOf(TelemonitoringDomainException.class);
    }

    @Test
    void staleExpectedVersionIsRejected() {
        MonitoringPlanEntity plan = newPlan();
        planService.amendThresholds(plan.getId(),
                new AmendThresholdsCommand("{\"a\":1}", "first amendment", "clinician-b", 1));
        // A second caller still holding version 1 must conflict, not silently fork.
        assertThatThrownBy(() -> planService.amendThresholds(plan.getId(),
                new AmendThresholdsCommand("{\"b\":2}", "stale amendment", "clinician-c", 1)))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("moved on");
    }

    @Test
    void duplicateVersionRaceIsBlockedByUniqueConstraint() {
        MonitoringPlanEntity plan = newPlan();
        ThresholdProfileEntity v1 = thresholdRepository.findFirstByPlanIdOrderByVersionDesc(plan.getId()).orElseThrow();

        // Simulate the losing side of a concurrent amendment: same (plan_id, version) pair.
        ThresholdProfileEntity duplicate = new ThresholdProfileEntity();
        duplicate.setPlanId(plan.getId());
        duplicate.setTenantId(plan.getTenantId());
        duplicate.setVersion(v1.getVersion());
        duplicate.setParameters("{}");
        duplicate.setCreatedBy("clinician-race");
        assertThatThrownBy(() -> thresholdRepository.saveAndFlush(duplicate))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void terminalPlanRejectsAmendment() {
        MonitoringPlanEntity plan = newPlan();
        planService.cancel(plan.getId(), "not needed", "clinician-a");
        assertThatThrownBy(() -> planService.amendThresholds(plan.getId(),
                new AmendThresholdsCommand("{}", "reason", "clinician-b", null)))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("CANCELLED");
    }
}
