package zw.gov.mohcc.impilo.procedures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.procedures.api.dto.AftercareDtos.AftercareTemplateView;
import zw.gov.mohcc.impilo.procedures.api.dto.AftercareDtos.InstructionView;
import zw.gov.mohcc.impilo.procedures.api.dto.AftercareDtos.RecoverySettingView;
import zw.gov.mohcc.impilo.procedures.core.ProceduresDomainException;
import zw.gov.mohcc.impilo.procedures.core.RecoveryAndAftercareService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §15 (recovery settings) and §17 (aftercare templates) — read layer, engine-not-store.
 *
 * <p>H2's {@code create-drop} schema does not enforce the Postgres CHECK constraints (closed
 * vocabularies, published-authority governance) — those were proven directly against real
 * Postgres via the migration's own manual application before this file was written, and are
 * proven again in {@code procedures-p9-recovery-aftercare-journeys.sh}. What these tests prove
 * is the service's own read/resolve logic: PUBLISHED-only visibility, tenant isolation, ordering,
 * and that a template's instructions and delivery channels are both resolved, not just one.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class RecoveryAndAftercareServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Autowired private RecoveryAndAftercareService service;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM procedures.aftercare_template_channel");
        jdbc.update("DELETE FROM procedures.aftercare_instruction");
        jdbc.update("DELETE FROM procedures.aftercare_template");
        jdbc.update("DELETE FROM procedures.recovery_setting");
    }

    private void recoverySetting(UUID tenantId, String code, String label, Integer minutes) {
        jdbc.update("""
                INSERT INTO procedures.recovery_setting
                  (id, tenant_id, setting_code, setting_label, minimum_observation_minutes,
                   discharge_readiness_criteria, monitoring_required)
                VALUES (?,?,?,?,?, 'criteria', 'monitoring')
                """, UUID.randomUUID(), tenantId, code, label, minutes);
    }

    private UUID aftercareTemplate(UUID tenantId, String code, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO procedures.aftercare_template
                  (id, tenant_id, template_code, template_name, applicable_setting, description,
                   source_citation, status, approving_authority, content_maturity)
                VALUES (?,?,?,?, 'THEATRE', 'desc', 'RESULTS.CRITICAL_ACKNOWLEDGEMENT', ?, 'MOHCC', 'ENGINEERING_SEED')
                """, id, tenantId, code, "Name of " + code, status);
        return id;
    }

    private void instruction(UUID templateId, String kind, String text, int order) {
        jdbc.update("""
                INSERT INTO procedures.aftercare_instruction (id, template_id, instruction_kind, instruction_text, display_order)
                VALUES (?,?,?,?,?)
                """, UUID.randomUUID(), templateId, kind, text, order);
    }

    private void channel(UUID templateId, String channel) {
        jdbc.update("""
                INSERT INTO procedures.aftercare_template_channel (template_id, delivery_channel)
                VALUES (?,?)
                """, templateId, channel);
    }

    @Test
    void recoverySettingsAreReturnedOrderedByCode() {
        recoverySetting(TENANT, "PACU", "Post-anaesthesia care unit", 60);
        recoverySetting(TENANT, "BEDSIDE_OBSERVATION", "Bedside observation", 15);

        List<RecoverySettingView> settings = service.recoverySettings(TENANT);

        assertThat(settings).extracting(RecoverySettingView::settingCode)
                .containsExactly("BEDSIDE_OBSERVATION", "PACU");
    }

    @Test
    void recoverySettingResolvesByCode() {
        recoverySetting(TENANT, "PACU", "Post-anaesthesia care unit", 60);

        RecoverySettingView v = service.recoverySetting(TENANT, "PACU");

        assertThat(v.settingLabel()).isEqualTo("Post-anaesthesia care unit");
        assertThat(v.minimumObservationMinutes()).isEqualTo(60);
    }

    @Test
    void anUnknownRecoverySettingCodeIsNotFound() {
        assertThatThrownBy(() -> service.recoverySetting(TENANT, "NOT_A_REAL_SETTING"))
                .isInstanceOf(ProceduresDomainException.class)
                .satisfies(e -> assertThat(((ProceduresDomainException) e).getCode())
                        .isEqualTo("RECOVERY_SETTING_NOT_FOUND"));
    }

    @Test
    void recoverySettingsFromAnotherTenantAreInvisible() {
        recoverySetting(OTHER_TENANT, "PACU", "Post-anaesthesia care unit", 60);

        assertThat(service.recoverySettings(TENANT)).isEmpty();
    }

    @Test
    void aPublishedTemplateResolvesWithInstructionsAndChannels() {
        UUID id = aftercareTemplate(TENANT, "AFTERCARE-THEATRE", "PUBLISHED");
        instruction(id, "WARNING_SIGNS", "Return if fever develops.", 2);
        instruction(id, "WOUND_SITE_CARE", "Keep the wound clean and dry.", 1);
        channel(id, "CLINICAL_SUMMARY");
        channel(id, "KHULUMA");

        AftercareTemplateView v = service.aftercareTemplate(TENANT, "AFTERCARE-THEATRE");

        assertThat(v.templateCode()).isEqualTo("AFTERCARE-THEATRE");
        assertThat(v.instructions()).extracting(InstructionView::instructionKind)
                .containsExactly("WOUND_SITE_CARE", "WARNING_SIGNS");
        assertThat(v.deliveryChannels()).containsExactlyInAnyOrder("CLINICAL_SUMMARY", "KHULUMA");
        assertThat(v.contentMaturity()).isEqualTo("ENGINEERING_SEED");
    }

    @Test
    void anUnpublishedTemplateIsNotFoundEvenIfTheCodeExists() {
        aftercareTemplate(TENANT, "AFTERCARE-DRAFT", "DRAFT");

        assertThatThrownBy(() -> service.aftercareTemplate(TENANT, "AFTERCARE-DRAFT"))
                .isInstanceOf(ProceduresDomainException.class)
                .satisfies(e -> assertThat(((ProceduresDomainException) e).getStatus()).isEqualTo(404));
    }

    @Test
    void anUnknownTemplateCodeIsNotFound() {
        assertThatThrownBy(() -> service.aftercareTemplate(TENANT, "AFTERCARE-NOT-REAL"))
                .isInstanceOf(ProceduresDomainException.class)
                .satisfies(e -> assertThat(((ProceduresDomainException) e).getCode())
                        .isEqualTo("AFTERCARE_TEMPLATE_NOT_FOUND"));
    }

    @Test
    void aTemplateFromAnotherTenantIsInvisible() {
        aftercareTemplate(OTHER_TENANT, "AFTERCARE-THEATRE", "PUBLISHED");

        assertThatThrownBy(() -> service.aftercareTemplate(TENANT, "AFTERCARE-THEATRE"))
                .isInstanceOf(ProceduresDomainException.class);
    }

    /** A template with no channel rows is still real content — delivery is a separate decision. */
    @Test
    void aTemplateWithNoChannelsResolvesWithAnEmptyChannelList() {
        UUID id = aftercareTemplate(TENANT, "AFTERCARE-BEDSIDE", "PUBLISHED");
        instruction(id, "WOUND_SITE_CARE", "Keep clean.", 1);

        AftercareTemplateView v = service.aftercareTemplate(TENANT, "AFTERCARE-BEDSIDE");

        assertThat(v.deliveryChannels()).isEmpty();
        assertThat(v.instructions()).hasSize(1);
    }
}
