package zw.gov.mohcc.impilo.experience.workcontext;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Proves that a programme appointment's authority tracks the programme registry's
 * lifecycle status, rather than surviving the programme it points at.
 *
 * <p>Each branch is asserted against the ACTIVE case as its own control, so a test
 * passing because nothing resolved at all would be visible.</p>
 */
@ExtendWith(MockitoExtension.class)
class GovernanceContextSourceProgrammeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private WorkforceGovernanceClient governanceClient;
    @Mock private WorkModeResolution modeResolution;

    private GovernanceContextSource source() {
        return new GovernanceContextSource(governanceClient, modeResolution,
                new ProgrammeRegistryLookup(governanceClient));
    }

    private void stubAssignment() {
        JsonNode assignments = json("""
                [{"id":"asg-1","targetType":"PROGRAM","targetId":"prog-1","roleCode":"PROGRAMME_MANAGER",
                  "roleCategory":"PROGRAMMATIC","roleLevel":"NATIONAL","primaryFlag":false,"secondaryFlag":false}]
                """);
        when(governanceClient.searchAssignments(anyString(), anyString(), anyString())).thenReturn(assignments);
        when(modeResolution.resolve(any(), any(), any(), any())).thenReturn(
                new WorkModeResolution.Resolution(List.of("PROGRAMME_MANAGEMENT"), "PROGRAMME_MANAGEMENT", "CATALOG"));
    }

    private void stubRegistry(String body) {
        when(governanceClient.getJson("/v1/internal/governance/programmes"))
                .thenReturn(body == null ? null : json(body));
    }

    private List<ResolvedWorkContext> resolve() {
        return source().resolve("actor-1", null).contexts();
    }

    @Test
    void activeProgramme_resolvesWithItsRegistryName_andNoRestriction() {
        stubAssignment();
        stubRegistry("""
                [{"id":"prog-1","name":"National TB Programme","status":"ACTIVE"}]
                """);

        List<ResolvedWorkContext> contexts = resolve();

        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0).contextKind()).isEqualTo("programme");
        assertThat(contexts.get(0).programmeId()).isEqualTo("prog-1");
        assertThat(contexts.get(0).label()).isEqualTo("National TB Programme (PROGRAMME_MANAGER)");
        assertThat(contexts.get(0).restrictions()).isEmpty();
    }

    @Test
    void closedProgramme_confersNoContextAtAll() {
        stubAssignment();
        stubRegistry("""
                [{"id":"prog-1","name":"National TB Programme","status":"CLOSED"}]
                """);

        assertThat(resolve()).isEmpty();
    }

    @Test
    void archivedProgramme_confersNoContextAtAll() {
        stubAssignment();
        stubRegistry("""
                [{"id":"prog-1","name":"National TB Programme","status":"ARCHIVED"}]
                """);

        assertThat(resolve()).isEmpty();
    }

    @Test
    void suspendedProgramme_stillResolvesButIsRestricted() {
        stubAssignment();
        stubRegistry("""
                [{"id":"prog-1","name":"National TB Programme","status":"SUSPENDED"}]
                """);

        List<ResolvedWorkContext> contexts = resolve();

        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0).restrictions()).containsExactly("PROGRAMME_SUSPENDED");
    }

    @Test
    void programmeAbsentFromRegistry_isMarkedUnresolved_notSilentlyTrusted() {
        stubAssignment();
        stubRegistry("""
                [{"id":"some-other-programme","name":"Malaria","status":"ACTIVE"}]
                """);

        List<ResolvedWorkContext> contexts = resolve();

        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0).restrictions()).containsExactly("PROGRAMME_UNRESOLVED");
    }

    @Test
    void registryUnreachable_marksStatusUnverified_ratherThanHidingTheDuty() {
        stubAssignment();
        stubRegistry(null);

        List<ResolvedWorkContext> contexts = resolve();

        // An outage must not read as "you have no programme duty" — that is the
        // degradation blindness the resolver exists to prevent.
        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0).restrictions()).containsExactly("PROGRAMME_STATUS_UNVERIFIED");
    }

    @Test
    void unrecognisedStatus_isNotTreatedAsActive() {
        stubAssignment();
        stubRegistry("""
                [{"id":"prog-1","name":"National TB Programme","status":"PENDING_APPROVAL"}]
                """);

        List<ResolvedWorkContext> contexts = resolve();

        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0).restrictions()).containsExactly("PROGRAMME_STATUS_UNRECOGNISED");
    }

    @Test
    void nonProgrammeContexts_areUnaffectedByTheRegistry() {
        JsonNode assignments = json("""
                [{"id":"asg-2","targetType":"JURISDICTION","targetId":"jur-1","jurisdictionCode":"ZW-HRE",
                  "roleCode":"DISTRICT_MANAGER","roleCategory":"ADMINISTRATIVE","roleLevel":"DISTRICT"}]
                """);
        when(governanceClient.searchAssignments(anyString(), anyString(), anyString())).thenReturn(assignments);
        when(modeResolution.resolve(any(), any(), any(), any())).thenReturn(
                new WorkModeResolution.Resolution(List.of("JURISDICTION_OPERATIONS"), "JURISDICTION_OPERATIONS", "CATALOG"));

        List<ResolvedWorkContext> contexts = resolve();

        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0).contextKind()).isEqualTo("jurisdiction");
        assertThat(contexts.get(0).restrictions()).isEmpty();
    }

    /**
     * The status discriminator itself, asserted in both directions.
     *
     * <p>The resolve-level tests above already discriminate — an implementation that
     * always returned a context would fail {@code closedProgramme_…}, and one that never
     * did would fail {@code activeProgramme_…}. This pins the predicate directly so a
     * future edit to the status vocabulary cannot quietly widen what confers authority.</p>
     */
    @Test
    void statusPredicates_discriminateInBothDirections() {
        assertThat(new ProgrammeRegistryLookup.Programme("p", "n", "CLOSED").confersNoAuthority()).isTrue();
        assertThat(new ProgrammeRegistryLookup.Programme("p", "n", "ARCHIVED").confersNoAuthority()).isTrue();
        assertThat(new ProgrammeRegistryLookup.Programme("p", "n", "ACTIVE").confersNoAuthority()).isFalse();
        assertThat(new ProgrammeRegistryLookup.Programme("p", "n", "SUSPENDED").confersNoAuthority()).isFalse();

        assertThat(new ProgrammeRegistryLookup.Programme("p", "n", "SUSPENDED").isSuspended()).isTrue();
        assertThat(new ProgrammeRegistryLookup.Programme("p", "n", "ACTIVE").isSuspended()).isFalse();

        assertThat(new ProgrammeRegistryLookup.Programme("p", "n", "ACTIVE").isActive()).isTrue();
        assertThat(new ProgrammeRegistryLookup.Programme("p", "n", "PENDING_APPROVAL").isActive()).isFalse();
        // A null status must not read as active.
        assertThat(new ProgrammeRegistryLookup.Programme("p", "n", null).isActive()).isFalse();
    }

    private static JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
