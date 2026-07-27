package zw.gov.mohcc.impilo.procedures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.procedures.api.dto.CatalogueDtos.CatalogueDetail;
import zw.gov.mohcc.impilo.procedures.api.dto.CatalogueDtos.CatalogueListResponse;
import zw.gov.mohcc.impilo.procedures.core.ProcedureCatalogueService;
import zw.gov.mohcc.impilo.procedures.core.ProceduresDomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Catalogue read behaviour. Uses ddl-auto schema with rows inserted directly, so this proves
 * the service and mapping; the SQL constraints and the seeded content are proven separately
 * against real Postgres by procedures-catalogue-journeys.sh, because H2 does not enforce the
 * partial unique index or the CHECK constraints faithfully.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProcedureCatalogueServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-4000-8000-0000000000ff");

    @Autowired
    private ProcedureCatalogueService catalogue;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM procedures.procedure_requirement");
        jdbc.update("DELETE FROM procedures.procedure_definition");
    }

    private UUID insertDefinition(UUID tenant, String code, String status, String laterality) {
        UUID id = UUID.randomUUID();
        // The JSON columns (synonyms, permitted_settings, technique_variants) are deliberately
        // omitted. H2 stores a JDBC-written '[]' as the JSON *string* "[]" rather than an empty
        // array, so asserting on them here would test H2's JSON handling rather than ours. Their
        // real behaviour is proven against Postgres by procedures-catalogue-journeys.sh, which is
        // the only place it means anything — H2 has masked a jsonb defect in this estate before.
        jdbc.update("""
                INSERT INTO procedures.procedure_definition
                  (id, tenant_id, definition_code, version, clinical_name, category,
                   owning_specialty, purpose, laterality_applicability,
                   pregnancy_applicability, recovery_required, status, approving_authority)
                VALUES (?,?,?,1,?,'THEATRE','SURGERY','THERAPEUTIC',?,'NO_CONSTRAINT',
                        false,?,'PENDING_MOHCC_RATIFICATION')
                """, id, tenant, code, "Name of " + code, laterality, status);
        return id;
    }

    private void insertRequirement(UUID definitionId, String kind, String code, boolean overridable) {
        jdbc.update("""
                INSERT INTO procedures.procedure_requirement
                  (id, definition_id, requirement_kind, requirement_code, requirement_label,
                   obligation, owner_role, overridable_in_emergency, on_resolver_unavailable, display_order)
                VALUES (?,?,?,?,?, 'MANDATORY','PERFORMING_CLINICIAN',?,'BLOCK',10)
                """, UUID.randomUUID(), definitionId, kind, code, kind + " label", overridable);
    }

    @Test
    void servesOnlyPublishedDefinitions() {
        insertDefinition(TENANT, "PROC-PUBLISHED", "PUBLISHED", "MIDLINE");
        insertDefinition(TENANT, "PROC-DRAFT", "DRAFT", "MIDLINE");
        insertDefinition(TENANT, "PROC-RETIRED", "RETIRED", "MIDLINE");

        CatalogueListResponse response = catalogue.search(TENANT, null, null, null);

        assertThat(response.items()).extracting("definitionCode").containsExactly("PROC-PUBLISHED");
        // A draft is content in progress and a retired entry describes a procedure the service
        // no longer governs. Serving either would let a procedure be judged against a standard
        // nobody approved.
        assertThatThrownBy(() -> catalogue.byCode(TENANT, "PROC-DRAFT"))
                .isInstanceOf(ProceduresDomainException.class)
                .hasMessageContaining("PROC-DRAFT");
    }

    @Test
    void isTenantScoped() {
        insertDefinition(TENANT, "PROC-MINE", "PUBLISHED", "MIDLINE");
        insertDefinition(OTHER_TENANT, "PROC-THEIRS", "PUBLISHED", "MIDLINE");

        assertThat(catalogue.search(TENANT, null, null, null).items())
                .extracting("definitionCode").containsExactly("PROC-MINE");
        assertThatThrownBy(() -> catalogue.byCode(TENANT, "PROC-THEIRS"))
                .isInstanceOf(ProceduresDomainException.class);
    }

    /**
     * The distinction the ADR makes a delivery requirement: a filter matching nothing and an
     * empty catalogue are different facts. A specialty menu that renders nothing because the
     * catalogue did not load silently removes capability from a clinician, and an empty array
     * alone cannot tell those apart.
     */
    @Test
    void reportsCatalogueSizeSoAnEmptyResultIsDistinguishableFromAnEmptyCatalogue() {
        CatalogueListResponse emptyCatalogue = catalogue.search(TENANT, null, null, null);
        assertThat(emptyCatalogue.matched()).isZero();
        assertThat(emptyCatalogue.catalogueSize()).isZero();

        insertDefinition(TENANT, "PROC-ONE", "PUBLISHED", "MIDLINE");

        CatalogueListResponse filterMatchedNothing = catalogue.search(TENANT, "CARDIOLOGY", null, null);
        assertThat(filterMatchedNothing.matched()).isZero();
        // Same empty item list, different meaning — and the caller can now tell.
        assertThat(filterMatchedNothing.catalogueSize()).isEqualTo(1);
    }

    @Test
    void summaryFlagsWhenASideMustBeCollected() {
        UUID lateralised = insertDefinition(TENANT, "PROC-LAT", "PUBLISHED", "LATERALISED");
        insertRequirement(lateralised, "SITE_SIDE_VERIFICATION", "SITE-SIDE-CONFIRMED", false);
        insertDefinition(TENANT, "PROC-MID", "PUBLISHED", "MIDLINE");

        CatalogueListResponse response = catalogue.search(TENANT, null, null, null);

        assertThat(response.items())
                .filteredOn(i -> i.definitionCode().equals("PROC-LAT"))
                .allMatch(i -> i.requiresSiteSideVerification());
        assertThat(response.items())
                .filteredOn(i -> i.definitionCode().equals("PROC-MID"))
                .allMatch(i -> !i.requiresSiteSideVerification());
    }

    @Test
    void detailExposesRequirementsWithOwnerAndOverridability() {
        UUID id = insertDefinition(TENANT, "PROC-DEEP", "PUBLISHED", "LATERALISED");
        insertRequirement(id, "SITE_SIDE_VERIFICATION", "SITE-SIDE-CONFIRMED", false);
        insertRequirement(id, "CONSENT", "CONSENT-SURGICAL", true);

        CatalogueDetail detail = catalogue.byCode(TENANT, "PROC-DEEP");

        assertThat(detail.requirements()).hasSize(2);
        // §8 — every requirement names an owner, because a blocker nobody owns is never cleared.
        assertThat(detail.requirements()).allMatch(r -> r.ownerRole() != null && !r.ownerRole().isBlank());
        assertThat(detail.requirements())
                .filteredOn(r -> r.requirementKind().equals("SITE_SIDE_VERIFICATION"))
                .allMatch(r -> !r.overridableInEmergency());
        assertThat(detail.requirements())
                .filteredOn(r -> r.requirementKind().equals("CONSENT"))
                .allMatch(r -> r.overridableInEmergency());
    }

    @Test
    void unratifiedContentSaysSoRatherThanLeavingTheAuthorityBlank() {
        insertDefinition(TENANT, "PROC-UNRATIFIED", "PUBLISHED", "MIDLINE");

        CatalogueDetail detail = catalogue.byCode(TENANT, "PROC-UNRATIFIED");

        assertThat(detail.approvingAuthority()).isEqualTo("PENDING_MOHCC_RATIFICATION");
    }
}
