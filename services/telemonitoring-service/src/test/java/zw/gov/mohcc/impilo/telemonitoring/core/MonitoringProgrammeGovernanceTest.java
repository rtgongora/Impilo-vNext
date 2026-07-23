package zw.gov.mohcc.impilo.telemonitoring.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.telemonitoring.core.MonitoringProgrammeService.CreateProgrammeCommand;
import zw.gov.mohcc.impilo.telemonitoring.core.MonitoringProgrammeService.UpdateProgrammeCommand;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.entity.MonitoringProgrammeEntity;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.telemonitoring.persistence.repository.MonitoringProgrammeRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * OF-B21 programme-catalogue governance: versioned metadata, effective windows, and the
 * retire-never-deletes invariant.
 */
@SpringBootTest
@ActiveProfiles("test")
class MonitoringProgrammeGovernanceTest {

    @Autowired private MonitoringProgrammeService programmeService;
    @Autowired private MonitoringPlanService planService;
    @Autowired private MonitoringProgrammeRepository programmeRepository;
    @Autowired private EventOutboxRepository outboxRepository;

    private final UUID tenant = UUID.randomUUID();

    private CreateProgrammeCommand createCmd(String code) {
        return new CreateProgrammeCommand(tenant, code, "Test programme " + code,
                "A governed test profile", "ZIBO-COND-1",
                "{\"spo2\":{\"greenMin\":94}}",
                "{\"workforceModel\":\"CHW_ADMINISTERED\",\"deviceClasses\":[\"pulse_oximeter\"]}",
                OffsetDateTime.now().minusDays(1), null, "governor-a");
    }

    @Test
    void createCarriesVersionedMetadataAndEffectiveWindow() {
        String code = code("CREATE");
        MonitoringProgrammeEntity programme = programmeService.create(createCmd(code));

        assertThat(programme.getVersion()).isEqualTo(1);
        assertThat(programme.getMetadata()).contains("workforceModel");
        assertThat(programme.getEffectiveFrom()).isNotNull();
        assertThat(programme.getEffectiveTo()).isNull();
        assertThat(programme.isActive()).isTrue();
        assertThat(programme.isRetired()).isFalse();
        assertThat(programme.getUpdatedBy()).isEqualTo("governor-a");
        assertThat(events(code)).containsExactly("telemonitoring.programme.created.v1");
    }

    @Test
    void duplicateCodeIsRejectedCodesAreNeverReused() {
        String code = code("DUP");
        programmeService.create(createCmd(code));
        assertThatThrownBy(() -> programmeService.create(createCmd(code)))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void invalidEffectiveWindowIsRejected() {
        OffsetDateTime now = OffsetDateTime.now();
        CreateProgrammeCommand bad = new CreateProgrammeCommand(tenant, code("WIN"), "Bad window",
                null, null, null, null, now, now.minusDays(1), "governor-a");
        assertThatThrownBy(() -> programmeService.create(bad))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("effectiveTo");
    }

    @Test
    void governedUpdateBumpsVersionAndRecordsActor() {
        String code = code("UPD");
        programmeService.create(createCmd(code));

        MonitoringProgrammeEntity updated = programmeService.update(code, new UpdateProgrammeCommand(
                tenant, "Renamed programme", null, null,
                "{\"spo2\":{\"greenMin\":92}}", "{\"revision\":\"clinical board 2026-07\"}",
                null, null, null, "governor-b"));

        assertThat(updated.getVersion()).isEqualTo(2);
        assertThat(updated.getName()).isEqualTo("Renamed programme");
        assertThat(updated.getDefaultThresholds()).contains("92");
        assertThat(updated.getMetadata()).contains("clinical board");
        assertThat(updated.getUpdatedBy()).isEqualTo("governor-b");
        assertThat(events(code)).containsExactly(
                "telemonitoring.programme.created.v1", "telemonitoring.programme.updated.v1");
    }

    @Test
    void updateRequiresAnIdentifiedGovernanceActor() {
        String code = code("ACT");
        programmeService.create(createCmd(code));
        assertThatThrownBy(() -> programmeService.update(code, new UpdateProgrammeCommand(
                tenant, "x", null, null, null, null, null, null, null, " ")))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("governance actor");
    }

    @Test
    void retireNeverDeletesAndRefusesNewPlans() {
        String code = code("RET");
        programmeService.create(createCmd(code));

        MonitoringProgrammeEntity retired = programmeService.retire(
                tenant, code, "Superseded by national revision", "governor-c");

        // Retire = deactivate + audit trail, never a DELETE.
        assertThat(retired.isRetired()).isTrue();
        assertThat(retired.isActive()).isFalse();
        assertThat(retired.getRetiredAt()).isNotNull();
        assertThat(retired.getRetiredBy()).isEqualTo("governor-c");
        assertThat(retired.getRetiredReason()).contains("Superseded");
        assertThat(programmeRepository.findByCode(code)).isPresent();

        // Retired profiles leave the active catalogue…
        assertThat(programmeRepository.findByActiveTrueOrderByCodeAsc())
                .noneMatch(p -> p.getCode().equals(code));

        // …and the strict plan-creation lane refuses them.
        assertThatThrownBy(() -> planService.createDraft(
                new MonitoringPlanService.CreatePlanCommand(tenant, "CPID-RET", code, null,
                        null, null, null, null, null, null, null, "clinician-a"), true))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining(code);

        // A retired profile is immutable — no further governance updates, no double retire.
        assertThatThrownBy(() -> programmeService.update(code, new UpdateProgrammeCommand(
                tenant, "zombie edit", null, null, null, null, null, null, null, "governor-c")))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("retired");
        assertThatThrownBy(() -> programmeService.retire(tenant, code, "again", "governor-c"))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("already retired");

        assertThat(events(code)).containsExactly(
                "telemonitoring.programme.created.v1", "telemonitoring.programme.retired.v1");
    }

    @Test
    void retireIsReasonBound() {
        String code = code("RSN");
        programmeService.create(createCmd(code));
        assertThatThrownBy(() -> programmeService.retire(tenant, code, " ", "governor-c"))
                .isInstanceOf(TelemonitoringDomainException.class)
                .hasMessageContaining("reason");
    }

    private String code(String prefix) {
        return "GOV_" + prefix + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private List<String> events(String programmeCode) {
        return outboxRepository.findByAggregateIdOrderByIdAsc(programmeCode).stream()
                .map(EventOutboxEntity::getEventType)
                .toList();
    }
}
