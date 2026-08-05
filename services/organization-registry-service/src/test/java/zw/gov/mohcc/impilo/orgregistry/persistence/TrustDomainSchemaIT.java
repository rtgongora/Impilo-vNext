package zw.gov.mohcc.impilo.orgregistry.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Database execution proof for V016 (Wave 1A).
 *
 * <p>A written migration is not proof. Every constraint asserted here — the exclusion
 * constraints, the CHECK pairs, the append-only triggers — lives in SQL that JVM unit
 * tests never execute, so until Flyway ran it against a real PostgreSQL nobody had
 * evidence it did anything. These run the actual migration in a container and then try
 * to break it.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class TrustDomainSchemaIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private JdbcTemplate jdbc;

    private UUID newTrustDomain(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO org_registry.trust_domain "
                + "(trust_domain_id, code, display_name, accreditation_status, key_custody_mode, "
                + " created_by_actor, version) VALUES (?,?,?,'PROVISIONAL','NATIONAL','test',0)",
                id, code, "Test " + code);
        return id;
    }

    // ── the migration itself ────────────────────────────────────────────────

    @Test
    @DisplayName("all eight tables create, and the migration is recorded as applied")
    void migrationApplies() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema='org_registry'",
                String.class);
        assertThat(tables).contains(
                "trust_domain", "trust_domain_relationship", "trust_domain_membership",
                "service_responsibility_profile", "service_agreement",
                "processing_role_assignment", "joint_controller_arrangement",
                "responsibility_audit_event");

        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version='016' AND success",
                Integer.class);
        assertThat(applied).isEqualTo(1);
    }

    @Test
    @DisplayName("a trust domain is creatable with NO controller identity at all (ADR-0055 d1)")
    void trustDomainNeedsNoController() {
        UUID id = newTrustDomain("TD-NO-CONTROLLER");
        String state = jdbc.queryForObject(
                "SELECT controller_declaration_state FROM org_registry.trust_domain WHERE trust_domain_id=?",
                String.class, id);
        assertThat(state).isEqualTo("UNDETERMINED");
        // controller_type is nullable too — all three descriptive fields, per ADR-0055 d1.
        Integer nulls = jdbc.queryForObject(
                "SELECT count(*) FROM org_registry.trust_domain WHERE trust_domain_id=? "
                        + "AND controller_type IS NULL AND data_controller_legal_name IS NULL "
                        + "AND data_controller_contact IS NULL", Integer.class, id);
        assertThat(nulls).isEqualTo(1);
    }

    @Test
    @DisplayName("a DECLARED controller without a name is refused by the CHECK pair")
    void declaredControllerNeedsAName() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO org_registry.trust_domain "
                        + "(trust_domain_id, code, display_name, controller_declaration_state, "
                        + " accreditation_status, key_custody_mode, created_by_actor, version) "
                        + "VALUES (?,?,?,'DECLARED','PROVISIONAL','NATIONAL','test',0)",
                UUID.randomUUID(), "TD-BAD-DECL", "Bad"))
                .hasMessageContaining("chk_trust_domain_declaration_consistent");
    }

    // ── exclusion constraints ───────────────────────────────────────────────

    @Test
    @DisplayName("two overlapping active legal controllers for one scope are refused")
    void singleActiveControllerEnforced() {
        UUID scope = UUID.randomUUID();
        jdbc.update("INSERT INTO org_registry.processing_role_assignment "
                + "(assignment_id, scope_type, scope_id, data_domain, purpose, legal_basis, "
                + " role_type, party_id, effective_from, status, recorded_by) "
                + "VALUES (?,'FACILITY',?,'encounters','TREATMENT','PUBLIC_TASK',"
                + "'LEGAL_CONTROLLER',?,now(),'ACTIVE','test')",
                UUID.randomUUID(), scope, UUID.randomUUID());

        assertThatThrownBy(() -> jdbc.update("INSERT INTO org_registry.processing_role_assignment "
                + "(assignment_id, scope_type, scope_id, data_domain, purpose, legal_basis, "
                + " role_type, party_id, effective_from, status, recorded_by) "
                + "VALUES (?,'FACILITY',?,'encounters','TREATMENT','PUBLIC_TASK',"
                + "'LEGAL_CONTROLLER',?,now(),'ACTIVE','test')",
                UUID.randomUUID(), scope, UUID.randomUUID()))
                .hasMessageContaining("ex_pra_single_active_controller");
    }

    @Test
    @DisplayName("joint controllers may coexist — joint control is not two controllers")
    void jointControllersCoexist() {
        UUID scope = UUID.randomUUID();
        for (int i = 0; i < 2; i++) {
            jdbc.update("INSERT INTO org_registry.processing_role_assignment "
                    + "(assignment_id, scope_type, scope_id, data_domain, purpose, legal_basis, "
                    + " role_type, party_id, effective_from, status, recorded_by) "
                    + "VALUES (?,'FACILITY',?,'encounters','TREATMENT','PUBLIC_TASK',"
                    + "'JOINT_CONTROLLER',?,now(),'ACTIVE','test')",
                    UUID.randomUUID(), scope, UUID.randomUUID());
        }
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM org_registry.processing_role_assignment "
                        + "WHERE scope_id=? AND role_type='JOINT_CONTROLLER'", Integer.class, scope);
        assertThat(n).isEqualTo(2);
    }

    @Test
    @DisplayName("a subject cannot hold two active trust-domain memberships at once")
    void singleActiveMembershipEnforced() {
        UUID subject = UUID.randomUUID();
        UUID a = newTrustDomain("TD-A-" + subject.toString().substring(0, 8));
        UUID b = newTrustDomain("TD-B-" + subject.toString().substring(0, 8));
        jdbc.update("INSERT INTO org_registry.trust_domain_membership "
                + "(membership_id, trust_domain_id, subject_type, subject_id, status, "
                + " effective_from, source_authority, provenance, created_by) "
                + "VALUES (?,?,'ORGANISATION',?,'ACTIVE',now(),'MOHCC_REGISTRY','{}'::jsonb,'test')",
                UUID.randomUUID(), a, subject);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO org_registry.trust_domain_membership "
                + "(membership_id, trust_domain_id, subject_type, subject_id, status, "
                + " effective_from, source_authority, provenance, created_by) "
                + "VALUES (?,?,'ORGANISATION',?,'ACTIVE',now(),'MOHCC_REGISTRY','{}'::jsonb,'test')",
                UUID.randomUUID(), b, subject))
                .hasMessageContaining("ex_tdm_one_active_per_subject");
    }

    @Test
    @DisplayName("membership evidence is mandatory, and unrecognised sources are refused")
    void membershipRequiresRecognisedEvidence() {
        UUID td = newTrustDomain("TD-EVIDENCE");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO org_registry.trust_domain_membership "
                + "(membership_id, trust_domain_id, subject_type, subject_id, status, "
                + " source_authority, provenance, created_by) "
                + "VALUES (?,?,'ORGANISATION',?,'PENDING_REVIEW','ORGANISATION_TYPE','{}'::jsonb,'test')",
                UUID.randomUUID(), td, UUID.randomUUID()))
                .hasMessageContaining("chk_tdm_source_authority");
    }

    @Test
    @DisplayName("only an ACTIVE membership may carry an effective period")
    void onlyActiveMembershipIsDated() {
        UUID td = newTrustDomain("TD-DATED");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO org_registry.trust_domain_membership "
                + "(membership_id, trust_domain_id, subject_type, subject_id, status, "
                + " effective_from, source_authority, provenance, created_by) "
                + "VALUES (?,?,'ORGANISATION',?,'PENDING_REVIEW',now(),'MOHCC_REGISTRY','{}'::jsonb,'test')",
                UUID.randomUUID(), td, UUID.randomUUID()))
                .hasMessageContaining("chk_tdm_active_dated");
    }

    // ── the controller may stay undetermined ────────────────────────────────

    @Test
    @DisplayName("a profile with RESOLVED state but no controller is refused; UNDETERMINED is fine")
    void controllerStatePairEnforced() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO org_registry.service_responsibility_profile "
                        + "(responsibility_profile_id, profile_code, hosting_model, "
                        + " controller_resolution_state, status, version, created_by_actor) "
                        + "VALUES (?,?,'NATIONAL_INFRASTRUCTURE','RESOLVED','DRAFT',1,'test')",
                UUID.randomUUID(), "P-BAD"))
                .hasMessageContaining("chk_srp_resolution_consistent");

        jdbc.update("INSERT INTO org_registry.service_responsibility_profile "
                + "(responsibility_profile_id, profile_code, hosting_model, "
                + " controller_resolution_state, status, version, created_by_actor) "
                + "VALUES (?,?,'NATIONAL_INFRASTRUCTURE','UNDETERMINED','DRAFT',1,'test')",
                UUID.randomUUID(), "P-OK");
        Integer n = jdbc.queryForObject("SELECT count(*) FROM "
                + "org_registry.service_responsibility_profile WHERE profile_code='P-OK'", Integer.class);
        assertThat(n).isEqualTo(1);
    }

    // ── audit is append-only ────────────────────────────────────────────────

    @Test
    @DisplayName("audit rows cannot be updated or deleted, by anyone")
    void auditIsAppendOnly() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO org_registry.responsibility_audit_event "
                + "(audit_event_id, actor_id, operation, target_type, decision) "
                + "VALUES (?,'actor','TEST_OP','TRUST_DOMAIN','PERMITTED')", id);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE org_registry.responsibility_audit_event SET decision='REFUSED' WHERE audit_event_id=?", id))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM org_registry.responsibility_audit_event WHERE audit_event_id=?", id))
                .hasMessageContaining("append-only");

        Integer still = jdbc.queryForObject("SELECT count(*) FROM "
                + "org_registry.responsibility_audit_event WHERE audit_event_id=?", Integer.class, id);
        assertThat(still).isEqualTo(1);
    }

    // ── nothing was fabricated by the migration ─────────────────────────────

    @Test
    @DisplayName("the migration mapped no organisation and backfilled no controller")
    void migrationFabricatedNothing() {
        Integer mapped = jdbc.queryForObject(
                "SELECT count(*) FROM org_registry.org_registry_organization WHERE trust_domain_id IS NOT NULL",
                Integer.class);
        assertThat(mapped).as("no organisation may be assigned a trust domain by migration").isZero();

        Integer memberships = jdbc.queryForObject(
                "SELECT count(*) FROM org_registry.trust_domain_membership", Integer.class);
        assertThat(memberships).as("membership is an evidenced act, never a migration side effect").isZero();

        Integer controllers = jdbc.queryForObject(
                "SELECT count(*) FROM org_registry.processing_role_assignment WHERE role_type='LEGAL_CONTROLLER'",
                Integer.class);
        assertThat(controllers).as("§26.2 #1 is open; no controller may be seeded").isZero();

        Integer domains = jdbc.queryForObject(
                "SELECT count(*) FROM org_registry.trust_domain WHERE code='MOHCC-ZW'", Integer.class);
        assertThat(domains).as("creating MOHCC-ZW would require declaring its controlling party").isZero();
    }

    @Test
    @DisplayName("history survives supersession — nothing is deleted")
    void historySurvivesSupersession() {
        UUID first = UUID.randomUUID();
        jdbc.update("INSERT INTO org_registry.service_responsibility_profile "
                + "(responsibility_profile_id, profile_code, hosting_model, status, version, created_by_actor) "
                + "VALUES (?,?,'INSTITUTION_PREMISES','ACTIVE',1,'test')", first, "P-HIST");
        UUID second = UUID.randomUUID();
        jdbc.update("INSERT INTO org_registry.service_responsibility_profile "
                + "(responsibility_profile_id, profile_code, hosting_model, status, version, "
                + " supersedes_profile_id, supersession_reason, created_by_actor) "
                + "VALUES (?,?,'INSTITUTION_PREMISES','DRAFT',2,?,'operator change','test')",
                second, "P-HIST", first);
        jdbc.update("UPDATE org_registry.service_responsibility_profile SET status='SUPERSEDED' "
                + "WHERE responsibility_profile_id=?", first);

        List<String> statuses = jdbc.queryForList(
                "SELECT status FROM org_registry.service_responsibility_profile "
                        + "WHERE profile_code='P-HIST' ORDER BY version", String.class);
        assertThat(statuses).containsExactly("SUPERSEDED", "DRAFT");
    }
}
