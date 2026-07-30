package zw.gov.mohcc.impilo.experience.workcontext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.client.OrganizationRegistryServiceClient;
import zw.gov.mohcc.impilo.experience.client.SupportServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoFacilityClaimClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The thin source adapters (E4).
 *
 * <p>The property that matters across all of them is the one the pre-Phase-C code
 * collapsed: "this source could not be reached" must never present as "this person has
 * no duty here". Every adapter below is asserted on both, because only the pair
 * distinguishes a working degradation signal from an adapter that returns nothing for
 * any reason at all.</p>
 */
@ExtendWith(MockitoExtension.class)
class WorkContextAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private WorkModeResolution modeResolution;

    private void stubModes(String... modes) {
        lenient().when(modeResolution.resolveForRoleTemplate(any()))
                .thenReturn(new WorkModeResolution.Resolution(List.of(modes),
                        modes.length > 0 ? modes[0] : null, "CATALOG"));
    }

    @Nested
    class Regulatory {

        @Mock private OrganizationRegistryServiceClient orgRegistryClient;

        private RegulatoryContextSource source() {
            return new RegulatoryContextSource(orgRegistryClient, modeResolution);
        }

        @Test
        void activeAppointmentResolvesAsRegulatorContext() {
            stubModes("REGULATORY_OVERSIGHT");
            when(orgRegistryClient.listAppointmentsByPerson(anyString())).thenReturn(json("""
                    [{"id":"app-1","organizationId":"org-1","roleCode":"INSPECTOR",
                      "jurisdictionCode":"ZW-HRE","status":"ACTIVE"}]
                    """));

            WorkContextAdapterResult result = source().resolve("actor-1");

            assertThat(result.contexts()).hasSize(1);
            assertThat(result.contexts().get(0).contextKind()).isEqualTo("regulator");
            assertThat(result.contexts().get(0).organisationId()).isEqualTo("org-1");
            assertThat(result.contexts().get(0).jurisdictionCode()).isEqualTo("ZW-HRE");
            assertThat(result.contexts().get(0).label()).isEqualTo("ZW-HRE — INSPECTOR");
            assertThat(result.status().state()).isEqualTo(WorkContextSourceStatus.State.LIVE);
        }

        @Test
        void nonActiveAppointmentIsExcluded() {
            when(orgRegistryClient.listAppointmentsByPerson(anyString())).thenReturn(json("""
                    [{"id":"app-1","organizationId":"org-1","roleCode":"INSPECTOR","status":"REVOKED"}]
                    """));

            assertThat(source().resolve("actor-1").contexts()).isEmpty();
        }

        @Test
        void appointmentPastItsValidToIsExcluded() {
            when(orgRegistryClient.listAppointmentsByPerson(anyString())).thenReturn(json("""
                    [{"id":"app-1","organizationId":"org-1","roleCode":"INSPECTOR",
                      "status":"ACTIVE","validTo":"2020-01-01"}]
                    """));

            assertThat(source().resolve("actor-1").contexts()).isEmpty();
        }

        @Test
        void upstreamFailureIsDegraded_notEmpty() {
            when(orgRegistryClient.listAppointmentsByPerson(anyString()))
                    .thenThrow(new RuntimeException("org-registry down"));

            WorkContextAdapterResult result = source().resolve("actor-1");

            assertThat(result.contexts()).isEmpty();
            assertThat(result.status().state()).isEqualTo(WorkContextSourceStatus.State.DEGRADED);
        }

        @Test
        void genuinelyNoAppointmentsIsEmpty_notDegraded() {
            when(orgRegistryClient.listAppointmentsByPerson(anyString())).thenReturn(json("[]"));

            WorkContextAdapterResult result = source().resolve("actor-1");

            assertThat(result.contexts()).isEmpty();
            assertThat(result.status().state()).isEqualTo(WorkContextSourceStatus.State.EMPTY);
        }

        @Test
        void blankActorIsEmpty_withoutCallingUpstream() {
            assertThat(source().resolve("").status().state()).isEqualTo(WorkContextSourceStatus.State.EMPTY);
            org.mockito.Mockito.verifyNoInteractions(orgRegistryClient);
        }
    }

    @Nested
    class Support {

        @Mock private SupportServiceClient supportClient;

        private SupportContextSource source() {
            return new SupportContextSource(supportClient, modeResolution);
        }

        @Test
        void facilityScopedTeamDefaultsToFacilitySupport() {
            stubModes("TECHNICAL_SUPPORT", "FACILITY_SUPPORT");
            when(supportClient.listSupportAssignmentsForPerson(anyString())).thenReturn(json("""
                    [{"assignmentId":"sa-1","teamId":"team-1","supportRole":"FIELD_ENGINEER","status":"ACTIVE"}]
                    """));
            when(supportClient.getSupportTeam("team-1")).thenReturn(json("""
                    {"name":"Harare Field Team","facilityScopeRef":"fac-1"}
                    """));

            WorkContextAdapterResult result = source().resolve("actor-1");

            assertThat(result.contexts()).hasSize(1);
            assertThat(result.contexts().get(0).defaultMode()).isEqualTo("FACILITY_SUPPORT");
            assertThat(result.contexts().get(0).facilityId()).isEqualTo("fac-1");
            assertThat(result.contexts().get(0).label()).isEqualTo("Harare Field Team (FIELD_ENGINEER)");
            assertThat(result.status().state()).isEqualTo(WorkContextSourceStatus.State.LIVE);
        }

        @Test
        void organisationScopedTeamDefaultsToTechnicalSupport() {
            stubModes("FACILITY_SUPPORT", "TECHNICAL_SUPPORT");
            when(supportClient.listSupportAssignmentsForPerson(anyString())).thenReturn(json("""
                    [{"assignmentId":"sa-1","teamId":"team-1","supportRole":"PLATFORM_ENGINEER","status":"ACTIVE"}]
                    """));
            when(supportClient.getSupportTeam("team-1")).thenReturn(json("""
                    {"name":"National Platform Team","organisationScope":"org-9"}
                    """));

            WorkContextAdapterResult result = source().resolve("actor-1");

            assertThat(result.contexts().get(0).defaultMode()).isEqualTo("TECHNICAL_SUPPORT");
            assertThat(result.contexts().get(0).organisationId()).isEqualTo("org-9");
        }

        @Test
        void inactiveAssignmentIsExcluded() {
            when(supportClient.listSupportAssignmentsForPerson(anyString())).thenReturn(json("""
                    [{"assignmentId":"sa-1","teamId":"team-1","supportRole":"FIELD_ENGINEER","status":"ENDED"}]
                    """));

            WorkContextAdapterResult result = source().resolve("actor-1");

            assertThat(result.contexts()).isEmpty();
            assertThat(result.status().state()).isEqualTo(WorkContextSourceStatus.State.EMPTY);
        }

        @Test
        void unreadableTeamScopeDegradesTheSource_butKeepsTheAssignmentVisible() {
            stubModes("TECHNICAL_SUPPORT");
            when(supportClient.listSupportAssignmentsForPerson(anyString())).thenReturn(json("""
                    [{"assignmentId":"sa-1","teamId":"team-1","supportRole":"FIELD_ENGINEER","status":"ACTIVE"}]
                    """));
            when(supportClient.getSupportTeam("team-1")).thenThrow(new RuntimeException("support down"));

            WorkContextAdapterResult result = source().resolve("actor-1");

            // The duty is real even when its scope could not be read; hiding it would be
            // the degradation blindness, and reporting it as LIVE would overstate.
            assertThat(result.contexts()).hasSize(1);
            assertThat(result.status().state()).isEqualTo(WorkContextSourceStatus.State.DEGRADED);
        }

        @Test
        void upstreamFailureIsDegraded_notEmpty() {
            when(supportClient.listSupportAssignmentsForPerson(anyString()))
                    .thenThrow(new RuntimeException("support down"));

            WorkContextAdapterResult result = source().resolve("actor-1");

            assertThat(result.contexts()).isEmpty();
            assertThat(result.status().state()).isEqualTo(WorkContextSourceStatus.State.DEGRADED);
        }

        @Test
        void genuinelyNoAssignmentsIsEmpty_notDegraded() {
            when(supportClient.listSupportAssignmentsForPerson(anyString())).thenReturn(json("[]"));

            assertThat(source().resolve("actor-1").status().state())
                    .isEqualTo(WorkContextSourceStatus.State.EMPTY);
        }
    }

    @Nested
    class FacilityAdmin {

        @Mock private TusoFacilityClaimClient tusoClient;

        private FacilityAdminContextSource source() {
            return new FacilityAdminContextSource(tusoClient, modeResolution);
        }

        @Test
        void appointmentResolvesAsFacilityContext() {
            stubModes("FACILITY_MANAGEMENT");
            when(tusoClient.appointmentsByPerson(anyString())).thenReturn(json("""
                    [{"id":"fa-1","facilityUuid":"fac-7","role":"FACILITY_ADMINISTRATOR"}]
                    """));

            WorkContextAdapterResult result = source().resolve("actor-1");

            assertThat(result.contexts()).hasSize(1);
            assertThat(result.contexts().get(0).contextKind()).isEqualTo("facility");
            assertThat(result.contexts().get(0).facilityId()).isEqualTo("fac-7");
            assertThat(result.contexts().get(0).groupHint()).isEqualTo("oversight");
            assertThat(result.status().state()).isEqualTo(WorkContextSourceStatus.State.LIVE);
        }

        @Test
        void upstreamFailureIsDegraded_notEmpty() {
            when(tusoClient.appointmentsByPerson(anyString())).thenThrow(new RuntimeException("tuso down"));

            WorkContextAdapterResult result = source().resolve("actor-1");

            assertThat(result.contexts()).isEmpty();
            assertThat(result.status().state()).isEqualTo(WorkContextSourceStatus.State.DEGRADED);
        }

        @Test
        void genuinelyNoAppointmentsIsEmpty_notDegraded() {
            when(tusoClient.appointmentsByPerson(anyString())).thenReturn(json("[]"));

            assertThat(source().resolve("actor-1").status().state())
                    .isEqualTo(WorkContextSourceStatus.State.EMPTY);
        }

        @Test
        void blankActorIsEmpty_withoutCallingUpstream() {
            assertThat(source().resolve(null).status().state()).isEqualTo(WorkContextSourceStatus.State.EMPTY);
            org.mockito.Mockito.verifyNoInteractions(tusoClient);
        }
    }

    private static JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
