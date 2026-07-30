package zw.gov.mohcc.impilo.experience.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient;
import zw.gov.mohcc.impilo.experience.workcontext.ResolvedWorkContext;
import zw.gov.mohcc.impilo.experience.workcontext.WorkContextResolutionService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The property under test is not "expired duties get revoked" — it is that this job
 * cannot turn a source outage into a national mass logout.
 */
@ExtendWith(MockitoExtension.class)
class WorkContextRevalidationJobTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private TshepoIdentityServiceClient identityClient;
    @Mock private WorkContextResolutionService resolutionService;

    private WorkContextRevalidationJob job(boolean revokeEnabled) {
        return job(revokeEnabled, 0.25);
    }

    private WorkContextRevalidationJob job(boolean revokeEnabled, double ratio) {
        return new WorkContextRevalidationJob(identityClient, resolutionService, true, revokeEnabled, 200, ratio);
    }

    private static JsonNode tokenFeed(int count) {
        List<String> rows = new ArrayList<>();
        IntStream.range(0, count).forEach(i -> rows.add(
                "{\"jti\":\"jti-" + i + "\",\"actorId\":\"actor-" + i + "\","
                        + "\"tenantId\":\"tenant-1\",\"contextId\":\"ctx-" + i + "\",\"workMode\":\"CLINICAL_CARE\"}"));
        return json("[" + String.join(",", rows) + "]");
    }

    private static ResolvedWorkContext ctx(String contextId) {
        return new ResolvedWorkContext(contextId, "facility", "VASHANDI", "rec", "fac-1", null, null, null,
                "ROLE", List.of(), List.of("CLINICAL_CARE"), "CLINICAL_CARE", "CATALOG",
                null, null, null, null, null, null, List.of(), "label", "regular", 0);
    }

    private static WorkContextResolutionService.ResolutionOutcome healthy(List<ResolvedWorkContext> contexts) {
        return new WorkContextResolutionService.ResolutionOutcome(
                contexts, List.of(), contexts.isEmpty() ? null : contexts.get(0).contextId(), false, null);
    }

    private static WorkContextResolutionService.ResolutionOutcome degraded(String state) {
        return new WorkContextResolutionService.ResolutionOutcome(List.of(), List.of(), null, false, state);
    }

    @Test
    void sourcesUnavailable_defersEveryToken_andRevokesNothing() {
        when(identityClient.listActiveWorkContextTokens(anyInt())).thenReturn(tokenFeed(5));
        when(resolutionService.resolve(anyString(), any())).thenReturn(degraded("work_context_unavailable"));

        WorkContextRevalidationJob.CycleReport report = job(true).runCycle();

        assertThat(report.deferred()).isEqualTo(5);
        assertThat(report.revoked()).isZero();
        verify(identityClient, never()).revokeScopedToken(anyString());
    }

    @Test
    void partiallyAvailableSources_alsoDefer_ratherThanActOnIncompleteEvidence() {
        when(identityClient.listActiveWorkContextTokens(anyInt())).thenReturn(tokenFeed(3));
        when(resolutionService.resolve(anyString(), any()))
                .thenReturn(degraded("work_context_partially_available"));

        WorkContextRevalidationJob.CycleReport report = job(true).runCycle();

        assertThat(report.deferred()).isEqualTo(3);
        verify(identityClient, never()).revokeScopedToken(anyString());
    }

    @Test
    void resolutionThrowing_isNotEvidenceADutyEnded() {
        when(identityClient.listActiveWorkContextTokens(anyInt())).thenReturn(tokenFeed(2));
        when(resolutionService.resolve(anyString(), any())).thenThrow(new RuntimeException("wgv down"));

        WorkContextRevalidationJob.CycleReport report = job(true).runCycle();

        assertThat(report.deferred()).isEqualTo(2);
        verify(identityClient, never()).revokeScopedToken(anyString());
    }

    @Test
    void healthyResolutionStillHoldingTheContext_leavesTheTokenAlone() {
        when(identityClient.listActiveWorkContextTokens(anyInt())).thenReturn(tokenFeed(3));
        when(resolutionService.resolve(anyString(), any()))
                .thenAnswer(inv -> {
                    String actor = inv.getArgument(0);
                    String idx = actor.substring("actor-".length());
                    return healthy(List.of(ctx("ctx-" + idx)));
                });

        WorkContextRevalidationJob.CycleReport report = job(true).runCycle();

        assertThat(report.stillValid()).isEqualTo(3);
        assertThat(report.revoked()).isZero();
        verify(identityClient, never()).revokeScopedToken(anyString());
    }

    @Test
    void healthyResolutionThatNoLongerHoldsTheContext_revokes() {
        // One token of eight is stale — under the circuit-breaker ratio, so it is acted on.
        when(identityClient.listActiveWorkContextTokens(anyInt())).thenReturn(tokenFeed(8));
        when(resolutionService.resolve(anyString(), any()))
                .thenAnswer(inv -> {
                    String actor = inv.getArgument(0);
                    String idx = actor.substring("actor-".length());
                    return "0".equals(idx) ? healthy(List.of()) : healthy(List.of(ctx("ctx-" + idx)));
                });

        WorkContextRevalidationJob.CycleReport report = job(true).runCycle();

        assertThat(report.revoked()).isEqualTo(1);
        assertThat(report.stillValid()).isEqualTo(7);
        verify(identityClient, times(1)).revokeScopedToken("jti-0");
    }

    @Test
    void circuitBreakerOpens_whenAnImplausibleShareWouldBeRevokedAtOnce() {
        // Every resolution is "healthy" but returns nothing — the shape a resolver defect
        // takes, which per-token degradation checks cannot detect.
        when(identityClient.listActiveWorkContextTokens(anyInt())).thenReturn(tokenFeed(10));
        when(resolutionService.resolve(anyString(), any())).thenReturn(healthy(List.of()));

        WorkContextRevalidationJob.CycleReport report = job(true).runCycle();

        assertThat(report.circuitOpen()).isTrue();
        assertThat(report.revoked()).isZero();
        assertThat(report.wouldRevoke()).isEqualTo(10);
        verify(identityClient, never()).revokeScopedToken(anyString());
    }

    @Test
    void shadowMode_measuresWithoutRevoking() {
        when(identityClient.listActiveWorkContextTokens(anyInt())).thenReturn(tokenFeed(8));
        when(resolutionService.resolve(anyString(), any()))
                .thenAnswer(inv -> {
                    String actor = inv.getArgument(0);
                    String idx = actor.substring("actor-".length());
                    return "0".equals(idx) ? healthy(List.of()) : healthy(List.of(ctx("ctx-" + idx)));
                });

        WorkContextRevalidationJob.CycleReport report = job(false).runCycle();

        assertThat(report.wouldRevoke()).isEqualTo(1);
        assertThat(report.revoked()).isZero();
        verify(identityClient, never()).revokeScopedToken(anyString());
    }

    @Test
    void emptyFeed_isANoOp() {
        when(identityClient.listActiveWorkContextTokens(anyInt())).thenReturn(json("[]"));

        assertThat(job(true).runCycle().examined()).isZero();
        verify(identityClient, never()).revokeScopedToken(anyString());
    }

    @Test
    void feedOutage_isANoOp_notAMassRevocation() {
        when(identityClient.listActiveWorkContextTokens(anyInt())).thenReturn(null);

        assertThat(job(true).runCycle().examined()).isZero();
        verify(identityClient, never()).revokeScopedToken(anyString());
    }

    @Test
    void disabledJob_doesNotEvenReadTheFeed() {
        new WorkContextRevalidationJob(identityClient, resolutionService, false, false, 200, 0.25).revalidate();

        verify(identityClient, never()).listActiveWorkContextTokens(anyInt());
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }

    private static JsonNode json(String raw) {
        try {
            return MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
