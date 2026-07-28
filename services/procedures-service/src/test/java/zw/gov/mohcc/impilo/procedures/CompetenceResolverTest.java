package zw.gov.mohcc.impilo.procedures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.procedures.api.dto.CompetenceDtos.CompetenceVerdict;
import zw.gov.mohcc.impilo.procedures.api.dto.CompetenceDtos.PrivilegeRecord;
import zw.gov.mohcc.impilo.procedures.core.CompetenceResolver;
import zw.gov.mohcc.impilo.procedures.core.PrivilegeSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §7 — "a trainee must not be surfaced as an independent authorised operator".
 *
 * <p>The privilege source is a controllable stand-in rather than a mock of the HTTP client,
 * so these tests exercise the resolver's real decision logic. What they deliberately do NOT
 * prove is the VARAPI wire contract — envelope shape, status vocabulary, scope naming — which
 * has bitten this estate three times and can only be proven against a running VARAPI.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class CompetenceResolverTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    /** Controllable privilege source: set what VARAPI would return, or make it fail. */
    static class StubPrivilegeSource implements PrivilegeSource {
        final List<PrivilegeRecord> records = new ArrayList<>();
        boolean unavailable = false;

        @Override
        public List<PrivilegeRecord> privilegesFor(String providerId) {
            if (unavailable) {
                throw new IllegalStateException("varapi unreachable");
            }
            return List.copyOf(records);
        }
    }

    @TestConfiguration
    static class Config {
        /**
         * {@code @Primary} rather than replacing the bean: the real VarapiPrivilegeClient stays in
         * the context so this test still proves the application wires up with its production
         * collaborator present. A stub that displaces the real bean entirely would let a
         * production wiring error pass every test in this class.
         */
        @Bean
        @Primary
        PrivilegeSource stubPrivilegeSource() {
            return new StubPrivilegeSource();
        }
    }

    @Autowired private CompetenceResolver resolver;
    @Autowired private PrivilegeSource privilegeSource;
    @Autowired private JdbcTemplate jdbc;

    private StubPrivilegeSource stub() {
        return (StubPrivilegeSource) privilegeSource;
    }

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM procedures.procedure_requirement");
        jdbc.update("DELETE FROM procedures.procedure_definition");
        stub().records.clear();
        stub().unavailable = false;
        define("PROC-X", "SURGERY");
    }

    private UUID define(String code, String specialty) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO procedures.procedure_definition
                  (id, tenant_id, definition_code, version, clinical_name, category, owning_specialty,
                   purpose, laterality_applicability, pregnancy_applicability, recovery_required,
                   status, approving_authority)
                VALUES (?,?,?,1,?, 'THEATRE',?,'THERAPEUTIC','MIDLINE','NO_CONSTRAINT',false,
                        'PUBLISHED','PENDING_MOHCC_RATIFICATION')
                """, id, TENANT, code, "Name of " + code, specialty);
        return id;
    }

    /** The finding this wave exists for. VARAPI has always held it; nobody read it. */
    @Test
    void aSupervisedPrivilegeIsNeverReportedAsIndependent() {
        stub().records.add(new PrivilegeRecord("SURGERY", "CLINICAL", "APPROVED", "prov-supervisor-1"));

        CompetenceVerdict v = resolver.resolve(TENANT, "prov-trainee", "PROC-X");

        assertThat(v.capacity()).isEqualTo("SUPERVISED");
        assertThat(v.mayProceedAlone()).isFalse();
        assertThat(v.supervisingProviderPublicId()).isEqualTo("prov-supervisor-1");
        // Supervision that leaves no signature is unreviewable.
        assertThat(v.countersignatureRequired()).isTrue();
    }

    @Test
    void anUnsupervisedApprovedPrivilegeIsIndependent() {
        stub().records.add(new PrivilegeRecord("SURGERY", "CLINICAL", "APPROVED", null));

        CompetenceVerdict v = resolver.resolve(TENANT, "prov-consultant", "PROC-X");

        assertThat(v.capacity()).isEqualTo("INDEPENDENT");
        assertThat(v.mayProceedAlone()).isTrue();
        assertThat(v.supervisingProviderPublicId()).isNull();
    }

    /**
     * A person may be independent for a specialty and separately supervised for a subspecialty.
     * The presence of the second must not demote the first.
     */
    @Test
    void holdingBothAnIndependentAndASupervisedPrivilegeResolvesToIndependent() {
        stub().records.add(new PrivilegeRecord("SURGERY", "CLINICAL", "APPROVED", "prov-supervisor-1"));
        stub().records.add(new PrivilegeRecord("SURGERY", "CLINICAL", "APPROVED", null));

        assertThat(resolver.resolve(TENANT, "prov-mixed", "PROC-X").capacity()).isEqualTo("INDEPENDENT");
    }

    @Test
    void aPendingOrRevokedPrivilegeIsNotAPrivilege() {
        stub().records.add(new PrivilegeRecord("SURGERY", "CLINICAL", "PENDING", null));
        stub().records.add(new PrivilegeRecord("SURGERY", "CLINICAL", "REVOKED", null));

        CompetenceVerdict v = resolver.resolve(TENANT, "prov-pending", "PROC-X");

        assertThat(v.capacity()).isEqualTo("NOT_PERMITTED");
        assertThat(v.mayProceedAlone()).isFalse();
    }

    @Test
    void aPrivilegeForAnotherSpecialtyDoesNotTransfer() {
        stub().records.add(new PrivilegeRecord("OPHTHALMOLOGY", "CLINICAL", "APPROVED", null));

        assertThat(resolver.resolve(TENANT, "prov-eye", "PROC-X").capacity()).isEqualTo("NOT_PERMITTED");
    }

    /** Fail safe. An outage is not a credential — and must not read as one in either direction. */
    @Test
    void anUnreachableCredentialingServiceIsUnknownNotIndependent() {
        stub().unavailable = true;

        CompetenceVerdict v = resolver.resolve(TENANT, "prov-any", "PROC-X");

        assertThat(v.capacity()).isEqualTo("UNKNOWN");
        assertThat(v.mayProceedAlone()).isFalse();
        assertThat(v.capacity()).isNotEqualTo("INDEPENDENT");
        assertThat(v.reasons()).anySatisfy(r -> assertThat(r).contains("unavailable"));
    }

    @Test
    void anUnknownProcedureIsUnknownRatherThanPermitted() {
        stub().records.add(new PrivilegeRecord("SURGERY", "CLINICAL", "APPROVED", null));

        CompetenceVerdict v = resolver.resolve(TENANT, "prov-consultant", "PROC-NOT-CATALOGUED");

        assertThat(v.capacity()).isEqualTo("UNKNOWN");
        assertThat(v.mayProceedAlone()).isFalse();
    }

    /** No path through the resolver returns mayProceedAlone for anything but INDEPENDENT. */
    @Test
    void onlyAnIndependentOperatorMayEverProceedAlone() {
        List<PrivilegeRecord[]> cases = List.of(
                new PrivilegeRecord[]{new PrivilegeRecord("SURGERY", "CLINICAL", "APPROVED", "sup-1")},
                new PrivilegeRecord[]{new PrivilegeRecord("SURGERY", "CLINICAL", "PENDING", null)},
                new PrivilegeRecord[]{new PrivilegeRecord("CARDIOLOGY", "CLINICAL", "APPROVED", null)},
                new PrivilegeRecord[]{});
        for (PrivilegeRecord[] c : cases) {
            stub().records.clear();
            stub().records.addAll(List.of(c));
            CompetenceVerdict v = resolver.resolve(TENANT, "prov", "PROC-X");
            assertThat(v.mayProceedAlone())
                    .as("capacity %s must not proceed alone", v.capacity()).isFalse();
        }
    }
}
