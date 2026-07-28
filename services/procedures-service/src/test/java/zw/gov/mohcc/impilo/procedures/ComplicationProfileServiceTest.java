package zw.gov.mohcc.impilo.procedures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.procedures.api.dto.ComplicationDtos.ClavienDindoGradeView;
import zw.gov.mohcc.impilo.procedures.api.dto.ComplicationDtos.ComplicationClassView;
import zw.gov.mohcc.impilo.procedures.api.dto.ComplicationDtos.ComplicationProfileView;
import zw.gov.mohcc.impilo.procedures.core.ComplicationProfileService;
import zw.gov.mohcc.impilo.procedures.core.ProceduresDomainException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §18 — Clavien-Dindo severity grades and complication profiles. Read layer, engine-not-store.
 *
 * <p>H2's {@code create-drop} schema does not enforce the Postgres CHECK constraints (closed
 * vocabularies, published-authority governance) — those are proven in
 * {@code procedures-complications-journeys.sh} against real Postgres. These tests prove the
 * service's own read/resolve logic: PUBLISHED-only visibility, tenant isolation, ordering, and
 * that a never-event class is surfaced with its flag intact (§18's own point — a never-event
 * must never read as an ordinary monitored risk).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ComplicationProfileServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Autowired private ComplicationProfileService service;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM procedures.complication_class");
        jdbc.update("DELETE FROM procedures.complication_profile");
        jdbc.update("DELETE FROM procedures.clavien_dindo_grade");
    }

    private void grade(UUID tenantId, String code, int order) {
        jdbc.update("""
                INSERT INTO procedures.clavien_dindo_grade
                  (id, tenant_id, grade_code, grade_label, description, display_order, source_citation)
                VALUES (?,?,?,?, 'desc', ?, 'Dindo 2004')
                """, UUID.randomUUID(), tenantId, code, "Grade " + code, order);
    }

    private UUID profile(UUID tenantId, String code, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO procedures.complication_profile
                  (id, tenant_id, profile_code, profile_name, applicable_setting, description,
                   source_citation, status, approving_authority, content_maturity)
                VALUES (?,?,?,?, 'THEATRE', 'desc', 'SURGERY.CLAVIEN_DINDO.SEVERITY_GRADING', ?, 'MOHCC', 'ENGINEERING_SEED')
                """, id, tenantId, code, "Name of " + code, status);
        return id;
    }

    private void complicationClass(UUID profileId, String type, String grade, boolean neverEvent, int order) {
        jdbc.update("""
                INSERT INTO procedures.complication_class
                  (id, profile_id, complication_type, typical_grade_code, monitoring_required,
                   escalation_action, is_never_event, display_order)
                VALUES (?,?,?,?, 'monitor', 'escalate', ?, ?)
                """, UUID.randomUUID(), profileId, type, grade, neverEvent, order);
    }

    @Test
    void clavienDindoGradesAreReturnedOrderedByDisplayOrder() {
        grade(TENANT, "V", 7);
        grade(TENANT, "I", 1);

        List<ClavienDindoGradeView> gs = service.clavienDindoGrades(TENANT);

        assertThat(gs).extracting(ClavienDindoGradeView::gradeCode).containsExactly("I", "V");
    }

    @Test
    void clavienDindoGradesFromAnotherTenantAreInvisible() {
        grade(OTHER_TENANT, "I", 1);

        assertThat(service.clavienDindoGrades(TENANT)).isEmpty();
    }

    @Test
    void aPublishedProfileResolvesWithItsClasses() {
        UUID id = profile(TENANT, "COMPLICATIONS-LAPAROTOMY", "PUBLISHED");
        complicationClass(id, "BLEEDING", "IIIA", false, 2);
        complicationClass(id, "SURGICAL_SITE_INFECTION", "II", false, 1);

        ComplicationProfileView v = service.complicationProfile(TENANT, "COMPLICATIONS-LAPAROTOMY");

        assertThat(v.profileCode()).isEqualTo("COMPLICATIONS-LAPAROTOMY");
        assertThat(v.classes()).extracting(ComplicationClassView::complicationType)
                .containsExactly("SURGICAL_SITE_INFECTION", "BLEEDING");
    }

    /** §18's own point: a never-event class must be surfaced distinctly, not as an ordinary risk. */
    @Test
    void aNeverEventClassIsSurfacedWithItsFlagIntact() {
        UUID id = profile(TENANT, "COMPLICATIONS-LAPAROTOMY", "PUBLISHED");
        complicationClass(id, "RETAINED_FOREIGN_OBJECT", null, true, 1);
        complicationClass(id, "BLEEDING", "IIIA", false, 2);

        ComplicationProfileView v = service.complicationProfile(TENANT, "COMPLICATIONS-LAPAROTOMY");

        ComplicationClassView neverEvent = v.classes().stream()
                .filter(c -> "RETAINED_FOREIGN_OBJECT".equals(c.complicationType())).findFirst().orElseThrow();
        assertThat(neverEvent.neverEvent()).isTrue();
        ComplicationClassView ordinary = v.classes().stream()
                .filter(c -> "BLEEDING".equals(c.complicationType())).findFirst().orElseThrow();
        assertThat(ordinary.neverEvent()).isFalse();
    }

    @Test
    void anUnpublishedProfileIsNotFoundEvenIfTheCodeExists() {
        profile(TENANT, "COMPLICATIONS-DRAFT", "DRAFT");

        assertThatThrownBy(() -> service.complicationProfile(TENANT, "COMPLICATIONS-DRAFT"))
                .isInstanceOf(ProceduresDomainException.class)
                .satisfies(e -> assertThat(((ProceduresDomainException) e).getStatus()).isEqualTo(404));
    }

    @Test
    void anUnknownProfileCodeIsNotFound() {
        assertThatThrownBy(() -> service.complicationProfile(TENANT, "COMPLICATIONS-NOT-REAL"))
                .isInstanceOf(ProceduresDomainException.class)
                .satisfies(e -> assertThat(((ProceduresDomainException) e).getCode())
                        .isEqualTo("COMPLICATION_PROFILE_NOT_FOUND"));
    }

    @Test
    void aProfileFromAnotherTenantIsInvisible() {
        profile(OTHER_TENANT, "COMPLICATIONS-LAPAROTOMY", "PUBLISHED");

        assertThatThrownBy(() -> service.complicationProfile(TENANT, "COMPLICATIONS-LAPAROTOMY"))
                .isInstanceOf(ProceduresDomainException.class);
    }

    @Test
    void aProfileWithNoClassesResolvesWithAnEmptyClassList() {
        profile(TENANT, "COMPLICATIONS-BEDSIDE-LINE", "PUBLISHED");

        ComplicationProfileView v = service.complicationProfile(TENANT, "COMPLICATIONS-BEDSIDE-LINE");

        assertThat(v.classes()).isEmpty();
    }
}
