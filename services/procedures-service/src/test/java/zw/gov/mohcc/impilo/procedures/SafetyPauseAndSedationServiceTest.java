package zw.gov.mohcc.impilo.procedures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.procedures.api.dto.SafetyPauseDtos.ConfirmationItemView;
import zw.gov.mohcc.impilo.procedures.api.dto.SafetyPauseDtos.SafetyPauseTemplateView;
import zw.gov.mohcc.impilo.procedures.api.dto.SafetyPauseDtos.SedationLevelView;
import zw.gov.mohcc.impilo.procedures.core.ProceduresDomainException;
import zw.gov.mohcc.impilo.procedures.core.SafetyPauseAndSedationService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §9 (safety-pause templates) and §10 (sedation continuum) — read layer only, engine-not-store.
 *
 * <p>H2's {@code create-drop} schema (see {@code application-test.yml}) does not enforce the
 * Postgres CHECK constraints or the composite rescue-capability FK that V005 declares — those
 * were mutation-proven directly against real Postgres before this file was written (13/13:
 * template/level counts, minimum confirmation items per template, invented-value rejection in
 * both directions, rescue-chain correctness, the sedation_level_code CHECK). What these tests
 * prove instead is the service's own read/resolve logic: PUBLISHED-only visibility, tenant
 * isolation, ordering, and — the point of {@link SafetyPauseAndSedationService#toView}, the
 * standard's own governing rule — that the rescue capability of the NEXT level up is resolved
 * inline rather than left as a code the caller must look up separately.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SafetyPauseAndSedationServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Autowired private SafetyPauseAndSedationService service;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM procedures.safety_pause_confirmation_item");
        jdbc.update("DELETE FROM procedures.safety_pause_template");
        jdbc.update("DELETE FROM procedures.sedation_level");
    }

    private UUID template(UUID tenantId, String code, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO procedures.safety_pause_template
                  (id, tenant_id, template_code, template_name, applicable_setting, description,
                   source_citation, status, approving_authority)
                VALUES (?,?,?,?, 'THEATRE', 'desc', 'WHO SSC 2009', ?, 'MOHCC')
                """, id, tenantId, code, "Name of " + code, status);
        return id;
    }

    private void item(UUID templateId, String confirmationItem, String prompt, int order) {
        jdbc.update("""
                INSERT INTO procedures.safety_pause_confirmation_item
                  (id, template_id, confirmation_item, prompt_text, display_order)
                VALUES (?,?,?,?,?)
                """, UUID.randomUUID(), templateId, confirmationItem, prompt, order);
    }

    private void sedationLevel(UUID tenantId, String code, String label, int rank, String rescueCode) {
        jdbc.update("""
                INSERT INTO procedures.sedation_level
                  (id, tenant_id, level_code, level_label, depth_rank, rescue_capability_level_code,
                   monitoring_required, provider_competence_required, typical_recovery_criteria,
                   source_citation)
                VALUES (?,?,?,?,?,?, 'monitoring', 'competence', 'criteria', 'WFSA-WHO SEDATION.CONTINUUM.DEPTH')
                """, UUID.randomUUID(), tenantId, code, label, rank, rescueCode);
    }

    @Test
    void aPublishedTemplateResolvesWithItsConfirmationItemsInDisplayOrder() {
        UUID id = template(TENANT, "SAFETY-PAUSE-SURGERY", "PUBLISHED");
        item(id, "TEAM", "Has everyone introduced themselves?", 2);
        item(id, "PATIENT", "Is the patient identity confirmed?", 1);

        SafetyPauseTemplateView v = service.safetyPauseTemplate(TENANT, "SAFETY-PAUSE-SURGERY");

        assertThat(v.templateCode()).isEqualTo("SAFETY-PAUSE-SURGERY");
        assertThat(v.confirmationItems()).extracting(ConfirmationItemView::confirmationItem)
                .containsExactly("PATIENT", "TEAM");
    }

    @Test
    void anUnpublishedTemplateIsNotFoundEvenIfTheCodeExists() {
        template(TENANT, "SAFETY-PAUSE-DRAFT", "DRAFT");

        assertThatThrownBy(() -> service.safetyPauseTemplate(TENANT, "SAFETY-PAUSE-DRAFT"))
                .isInstanceOf(ProceduresDomainException.class)
                .satisfies(e -> assertThat(((ProceduresDomainException) e).getStatus()).isEqualTo(404));
    }

    @Test
    void anUnknownTemplateCodeIsNotFound() {
        assertThatThrownBy(() -> service.safetyPauseTemplate(TENANT, "SAFETY-PAUSE-NOT-REAL"))
                .isInstanceOf(ProceduresDomainException.class)
                .satisfies(e -> assertThat(((ProceduresDomainException) e).getCode())
                        .isEqualTo("SAFETY_PAUSE_TEMPLATE_NOT_FOUND"));
    }

    @Test
    void aTemplateFromAnotherTenantIsInvisible() {
        template(OTHER_TENANT, "SAFETY-PAUSE-SURGERY", "PUBLISHED");

        assertThatThrownBy(() -> service.safetyPauseTemplate(TENANT, "SAFETY-PAUSE-SURGERY"))
                .isInstanceOf(ProceduresDomainException.class);
    }

    @Test
    void sedationLevelsAreReturnedOrderedByDepthNotInsertOrder() {
        sedationLevel(TENANT, "MODERATE_SEDATION", "Moderate sedation", 2, "DEEP_SEDATION");
        sedationLevel(TENANT, "NO_SEDATION", "No sedation", 0, null);
        sedationLevel(TENANT, "DEEP_SEDATION", "Deep sedation", 3, "GENERAL_ANAESTHESIA");

        List<SedationLevelView> levels = service.sedationLevels(TENANT);

        assertThat(levels).extracting(SedationLevelView::levelCode)
                .containsExactly("NO_SEDATION", "MODERATE_SEDATION", "DEEP_SEDATION");
    }

    /** The standard's own governing rule: requirements follow the depth that MAY be reached. */
    @Test
    void aSedationLevelResolvesTheNextLevelsRescueCapabilityInline() {
        sedationLevel(TENANT, "MODERATE_SEDATION", "Moderate sedation", 2, "DEEP_SEDATION");
        sedationLevel(TENANT, "DEEP_SEDATION", "Deep sedation", 3, null);

        SedationLevelView v = service.sedationLevel(TENANT, "MODERATE_SEDATION");

        assertThat(v.rescueCapability()).isNotNull();
        assertThat(v.rescueCapability().levelCode()).isEqualTo("DEEP_SEDATION");
        assertThat(v.rescueCapability().monitoringRequired()).isEqualTo("monitoring");
    }

    /** General anaesthesia sits at the top of the continuum — there is nothing above it to rescue into. */
    @Test
    void theDeepestLevelHasNoRescueCapability() {
        sedationLevel(TENANT, "GENERAL_ANAESTHESIA", "General anaesthesia", 5, null);

        SedationLevelView v = service.sedationLevel(TENANT, "GENERAL_ANAESTHESIA");

        assertThat(v.rescueCapability()).isNull();
    }

    @Test
    void anUnknownSedationLevelCodeIsNotFound() {
        assertThatThrownBy(() -> service.sedationLevel(TENANT, "NOT_A_REAL_LEVEL"))
                .isInstanceOf(ProceduresDomainException.class)
                .satisfies(e -> assertThat(((ProceduresDomainException) e).getCode())
                        .isEqualTo("SEDATION_LEVEL_NOT_FOUND"));
    }

    @Test
    void sedationLevelsFromAnotherTenantAreInvisible() {
        sedationLevel(OTHER_TENANT, "NO_SEDATION", "No sedation", 0, null);

        assertThat(service.sedationLevels(TENANT)).isEmpty();
    }
}
