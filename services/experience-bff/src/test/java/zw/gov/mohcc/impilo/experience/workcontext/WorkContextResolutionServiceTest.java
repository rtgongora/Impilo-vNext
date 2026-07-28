package zw.gov.mohcc.impilo.experience.workcontext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Proves the resolver's degradation semantics — the property the pre-Phase-C
 * code collapsed: "all sources unavailable" must read differently from
 * "genuinely no assignments" (see {@code WorkContextResolutionService}).
 */
@ExtendWith(MockitoExtension.class)
class WorkContextResolutionServiceTest {

    @Mock private VarapiServiceClient varapiClient;
    @Mock private VashandiContextSource vashandiSource;
    @Mock private GovernanceContextSource governanceSource;
    @Mock private RegulatoryContextSource regulatorySource;
    @Mock private FacilityAdminContextSource facilityAdminSource;
    @Mock private SupportContextSource supportSource;

    private WorkContextResolutionService service() {
        return new WorkContextResolutionService(varapiClient, vashandiSource, governanceSource,
                regulatorySource, facilityAdminSource, supportSource);
    }

    private static ResolvedWorkContext ctx(String contextId, String kind, String source, int score) {
        return new ResolvedWorkContext(contextId, kind, source, "rec-" + contextId,
                "facility-1", null, null, null, "ROLE", List.of(), List.of("CLINICAL_CARE"), "CLINICAL_CARE",
                "CATALOG", null, null, null, null, null, null, List.of(), "label-" + contextId, "regular", score);
    }

    @Test
    void allSourcesEmpty_yieldsNoFriendlyState_notUnavailable() {
        when(varapiClient.getProviderByHealthId("actor-1")).thenReturn(null);
        when(vashandiSource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty("VASHANDI")));
        when(governanceSource.resolve("actor-1", null)).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty("WGV")));
        when(regulatorySource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty("ORG_REGISTRY")));
        when(facilityAdminSource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty("TUSO")));
        when(supportSource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty("SUPPORT")));

        WorkContextResolutionService.ResolutionOutcome outcome = service().resolve("actor-1", null);

        assertThat(outcome.contexts()).isEmpty();
        assertThat(outcome.friendlyResolutionState()).isNull();
    }

    @Test
    void allSourcesDegraded_yieldsWorkContextUnavailable_distinctFromEmpty() {
        when(varapiClient.getProviderByHealthId("actor-1")).thenReturn(null);
        when(vashandiSource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.degraded("VASHANDI", "timeout")));
        when(governanceSource.resolve("actor-1", null)).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.degraded("WGV", "timeout")));
        when(regulatorySource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.degraded("ORG_REGISTRY", "timeout")));
        when(facilityAdminSource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.degraded("TUSO", "timeout")));
        when(supportSource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.degraded("SUPPORT", "timeout")));

        WorkContextResolutionService.ResolutionOutcome outcome = service().resolve("actor-1", null);

        assertThat(outcome.contexts()).isEmpty();
        assertThat(outcome.friendlyResolutionState()).isEqualTo("work_context_unavailable");
    }

    @Test
    void oneSourceDegraded_othersLive_returnsPartialResultsNotBlank() {
        when(varapiClient.getProviderByHealthId("actor-1")).thenReturn(null);
        when(vashandiSource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(ctx("c1", "facility", "VASHANDI", 0)), WorkContextSourceStatus.live("VASHANDI")));
        when(governanceSource.resolve("actor-1", null)).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.degraded("WGV", "timeout")));
        when(regulatorySource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty("ORG_REGISTRY")));
        when(facilityAdminSource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty("TUSO")));
        when(supportSource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty("SUPPORT")));

        WorkContextResolutionService.ResolutionOutcome outcome = service().resolve("actor-1", null);

        assertThat(outcome.contexts()).hasSize(1);
        assertThat(outcome.friendlyResolutionState()).isEqualTo("work_context_partially_available");
    }

    @Test
    void singleContext_neverRequiresChooser() {
        when(varapiClient.getProviderByHealthId("actor-1")).thenReturn(null);
        when(vashandiSource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(ctx("only", "facility", "VASHANDI", 0)), WorkContextSourceStatus.live("VASHANDI")));
        when(governanceSource.resolve("actor-1", null)).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty("WGV")));
        when(regulatorySource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty("ORG_REGISTRY")));
        when(facilityAdminSource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty("TUSO")));
        when(supportSource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty("SUPPORT")));

        WorkContextResolutionService.ResolutionOutcome outcome = service().resolve("actor-1", null);

        assertThat(outcome.contexts()).hasSize(1);
        assertThat(outcome.requiresContextChooser()).isFalse();
        assertThat(outcome.recommendedContextId()).isEqualTo("only");
    }

    @Test
    void proveContext_reResolvesFromSource_ratherThanTrustingACachedId() {
        when(varapiClient.getProviderByHealthId("actor-1")).thenReturn(null);
        ResolvedWorkContext live = ctx("live-ctx", "facility", "VASHANDI", 0);
        when(vashandiSource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(live), WorkContextSourceStatus.live("VASHANDI")));
        when(governanceSource.resolve("actor-1", null)).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty("WGV")));
        when(regulatorySource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty("ORG_REGISTRY")));
        when(facilityAdminSource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty("TUSO")));
        when(supportSource.resolve("actor-1")).thenReturn(
                new WorkContextAdapterResult(List.of(), WorkContextSourceStatus.empty("SUPPORT")));

        WorkContextResolutionService svc = service();
        assertThat(svc.proveContext("actor-1", null, live.contextId())).isNotNull();
        assertThat(svc.proveContext("actor-1", null, "some-id-nobody-proved")).isNull();
    }
}
