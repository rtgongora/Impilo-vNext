package zw.gov.mohcc.impilo.experience.workhome;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext;
import zw.gov.mohcc.impilo.experience.workcontext.WorkContextResolutionService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkHomeCompositionServiceTest {

    private static final String ACTOR_ID = "actor-1";
    private static final String CONTEXT_ID = "ctx-1";

    @Mock
    private WorkContextResolutionService resolutionService;

    private final ContextPropagatingFanout fanout = new ContextPropagatingFanout();
    private final AtomicInteger callCount = new AtomicInteger();

    private WorkHomeCompositionService service;
    private FakeAdapter facilityClinicalAdapter;

    @BeforeEach
    void setUp() {
        callCount.set(0);
        facilityClinicalAdapter = new FakeAdapter(WorkHomeFamily.FACILITY_CLINICAL, callCount);
        service = new WorkHomeCompositionService(resolutionService, fanout, List.of(facilityClinicalAdapter));
    }

    private ResolvedWorkContext contextWithModes(List<String> availableModes, String defaultMode) {
        return new ResolvedWorkContext(
                CONTEXT_ID, "facility", "VASHANDI", "rec-1",
                "facility-uuid-1", null, null, null,
                "role-1", List.of(), availableModes, defaultMode, "CATALOG",
                null, null, null, null, null,
                null, List.of(), "Test facility", "today", 500);
    }

    @Test
    void compose_contextCannotBeReproven_returnsWorkContextUnavailableWithNoSections() {
        when(resolutionService.proveContext(ACTOR_ID, null, CONTEXT_ID)).thenReturn(null);

        WorkHomeResult result = service.compose(ACTOR_ID, null, CONTEXT_ID, "CLINICAL_CARE");

        assertThat(result.friendlyState()).isEqualTo("work_context_unavailable");
        assertThat(result.sections()).isEmpty();
        assertThat(result.contextId()).isNull();
    }

    @Test
    void compose_modeNotAmongTheContextsAvailableModes_returnsWorkModeUnavailable() {
        ResolvedWorkContext ctx = contextWithModes(List.of("FACILITY_MANAGEMENT"), "FACILITY_MANAGEMENT");
        when(resolutionService.proveContext(ACTOR_ID, null, CONTEXT_ID)).thenReturn(ctx);

        // Client asks for CLINICAL_CARE but the re-proven context only authorises FACILITY_MANAGEMENT,
        // and no adapter is registered for FACILITY_MANAGEMENT in this test's service instance.
        WorkHomeResult result = service.compose(ACTOR_ID, null, CONTEXT_ID, "CLINICAL_CARE");

        assertThat(result.friendlyState()).isEqualTo("work_mode_unavailable");
        assertThat(result.sections()).isEmpty();
    }

    @Test
    void compose_validModeWithNoAdapterRegistered_returnsWorkModeUnavailable() {
        ResolvedWorkContext ctx = contextWithModes(List.of("PROGRAMME_MANAGEMENT"), "PROGRAMME_MANAGEMENT");
        when(resolutionService.proveContext(ACTOR_ID, null, CONTEXT_ID)).thenReturn(ctx);

        WorkHomeResult result = service.compose(ACTOR_ID, null, CONTEXT_ID, "PROGRAMME_MANAGEMENT");

        assertThat(result.friendlyState()).isEqualTo("work_mode_unavailable");
    }

    @Test
    void compose_validModeMatchingAdapter_returnsSectionsFromThatAdapterOnly() {
        ResolvedWorkContext ctx = contextWithModes(List.of("CLINICAL_CARE"), "CLINICAL_CARE");
        when(resolutionService.proveContext(ACTOR_ID, null, CONTEXT_ID)).thenReturn(ctx);

        WorkHomeResult result = service.compose(ACTOR_ID, null, CONTEXT_ID, "CLINICAL_CARE");

        assertThat(result.friendlyState()).isNull();
        assertThat(result.family()).isEqualTo("FACILITY_CLINICAL");
        assertThat(result.contextId()).isEqualTo(CONTEXT_ID);
        assertThat(result.sections()).hasSize(1);
        assertThat(result.sections().get(0).sectionId()).isEqualTo("fake-section");
        assertThat(result.sections().get(0).status()).isEqualTo("OK");
    }

    @Test
    void compose_missingRequestedMode_fallsBackToContextsDefaultMode() {
        ResolvedWorkContext ctx = contextWithModes(List.of("CLINICAL_CARE"), "CLINICAL_CARE");
        when(resolutionService.proveContext(ACTOR_ID, null, CONTEXT_ID)).thenReturn(ctx);

        WorkHomeResult result = service.compose(ACTOR_ID, null, CONTEXT_ID, null);

        assertThat(result.mode()).isEqualTo("CLINICAL_CARE");
        assertThat(result.family()).isEqualTo("FACILITY_CLINICAL");
    }

    @Test
    void compose_adapterSectionThrows_degradesThatSectionInsteadOfFailingTheWholeResponse() {
        FakeAdapter throwingAdapter = FakeAdapter.throwing(WorkHomeFamily.FACILITY_CLINICAL);
        WorkHomeCompositionService throwingService =
                new WorkHomeCompositionService(resolutionService, fanout, List.of(throwingAdapter));
        ResolvedWorkContext ctx = contextWithModes(List.of("CLINICAL_CARE"), "CLINICAL_CARE");
        when(resolutionService.proveContext(ACTOR_ID, null, CONTEXT_ID)).thenReturn(ctx);

        WorkHomeResult result = throwingService.compose(ACTOR_ID, null, CONTEXT_ID, "CLINICAL_CARE");

        assertThat(result.friendlyState()).isNull();
        assertThat(result.sections()).hasSize(1);
        assertThat(result.sections().get(0).status()).isEqualTo("DEGRADED");
    }

    @Test
    void composeSection_onlyRunsTheRequestedSectionTaskNotEveryTaskInTheAdapter() {
        ResolvedWorkContext ctx = contextWithModes(List.of("CLINICAL_CARE"), "CLINICAL_CARE");
        when(resolutionService.proveContext(ACTOR_ID, null, CONTEXT_ID)).thenReturn(ctx);

        WorkHomeSection section = service.composeSection(ACTOR_ID, null, CONTEXT_ID, "CLINICAL_CARE", "fake-section");

        assertThat(section.sectionId()).isEqualTo("fake-section");
        assertThat(section.status()).isEqualTo("OK");
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void composeSection_unknownSectionId_returnsEmptyNotAnException() {
        ResolvedWorkContext ctx = contextWithModes(List.of("CLINICAL_CARE"), "CLINICAL_CARE");
        when(resolutionService.proveContext(ACTOR_ID, null, CONTEXT_ID)).thenReturn(ctx);

        WorkHomeSection section = service.composeSection(ACTOR_ID, null, CONTEXT_ID, "CLINICAL_CARE", "no-such-section");

        assertThat(section.status()).isEqualTo("EMPTY");
    }

    /** Minimal adapter double — one section, optionally throwing, counting invocations. */
    private static final class FakeAdapter implements WorkHomeFamilyAdapter {
        private final WorkHomeFamily family;
        private final AtomicInteger callCount;
        private final boolean throwOnCompose;

        FakeAdapter(WorkHomeFamily family, AtomicInteger callCount) {
            this(family, callCount, false);
        }

        private FakeAdapter(WorkHomeFamily family, AtomicInteger callCount, boolean throwOnCompose) {
            this.family = family;
            this.callCount = callCount;
            this.throwOnCompose = throwOnCompose;
        }

        static FakeAdapter throwing(WorkHomeFamily family) {
            return new FakeAdapter(family, new AtomicInteger(), true);
        }

        @Override
        public WorkHomeFamily family() {
            return family;
        }

        @Override
        public List<FanoutTask<WorkHomeSection>> sectionTasks(ResolvedWorkContext context) {
            return List.of(FanoutTask.of("fake-section", () -> {
                callCount.incrementAndGet();
                if (throwOnCompose) {
                    throw new IllegalStateException("downstream unavailable");
                }
                return WorkHomeSection.ok("fake-section", "Fake section",
                        List.of(Map.of("id", "1", "status", "OPEN", "priority", "MEDIUM")), Map.of("total", 1));
            }));
        }
    }
}
