package zw.gov.mohcc.impilo.ndila.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaTshepoGuard;
import zw.gov.mohcc.impilo.ndila.core.routing.NdilaRouteService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Every routing handler must forward the caller's actor identity into the operation context.
 *
 * <p>The regression: {@code distance-matrix}, {@code routes/multi-stop} and {@code eta} declared
 * only {@code X-Tenant-ID} and {@code X-Correlation-ID}, and hardcoded {@code actorId}/{@code
 * actorType} to {@code null} when building their context. All three ask the guard for an
 * {@code INTERNAL} operation, and the guard denies {@code INTERNAL} outright when the actor is
 * absent — so the denial was <b>structural</b>: no token, header or role could ever satisfy it,
 * while the sibling {@code /routes} handler on the same controller worked. Downstream, the citizen
 * find-care map read the denial as a routing failure and showed a null ETA.</p>
 *
 * <p>Two things make this defect easy to miss, and the test is shaped around both:</p>
 * <ul>
 *   <li><b>The denial is an HTTP 200.</b> The guard verdict travels in the body as
 *       {@code success:false} / {@code denialReason}. A test asserting only on status code passes
 *       against the broken handler, which is why these assertions read the body.</li>
 *   <li><b>Both sides must not be synthesised.</b> The guard here is the real
 *       {@link NdilaTshepoGuard} run over the context the real controller actually built from real
 *       request headers. Only the routing provider is stubbed. A test that constructed its own
 *       context would prove the guard's logic and nothing about the wiring that broke.</li>
 * </ul>
 */
class NdilaRoutingControllerActorHeaderTest {

    private static final String TENANT = "11111111-4000-8000-9000-000000000001";
    private static final String CORRELATION = "22222222-4000-8000-9000-000000000002";

    private final NdilaTshepoGuard guard = new NdilaTshepoGuard();
    private final NdilaRouteService routeService = mock(NdilaRouteService.class);
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new NdilaRoutingController(routeService))
            .build();

    /**
     * Stubs the route service so that authorization is decided by the <em>real</em> guard, over the
     * context the controller built. A successful decision yields a plausible route; a denial is
     * surfaced exactly as the production service surfaces it.
     */
    private void routeServiceDefersToTheRealGuard() {
        when(routeService.route(any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenAnswer(inv -> {
                    NdilaTshepoGuard.Decision d = guard.authorize(inv.getArgument(0), "route");
                    if (!d.allowed()) {
                        return new NdilaRouteService.RouteResponse(false, d.reason(),
                                0, 0, null, null, null, false, false, null, List.of(), 0, null);
                    }
                    return new NdilaRouteService.RouteResponse(true, null,
                            4200, 480, "CAR", "MOCK", "MOCK", false, false, null, List.of(), 0.9,
                            "decision-1");
                });
        when(routeService.distanceMatrix(any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    NdilaTshepoGuard.Decision d = guard.authorize(inv.getArgument(0), "distance-matrix");
                    if (!d.allowed()) {
                        return new NdilaRouteService.DistanceMatrixResponse(false, d.reason(),
                                new double[0][0], new double[0][0], null);
                    }
                    return new NdilaRouteService.DistanceMatrixResponse(true, null,
                            new double[][]{{4200}}, new double[][]{{480}}, "MOCK");
                });
        when(routeService.multiStop(any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    NdilaTshepoGuard.Decision d = guard.authorize(inv.getArgument(0), "route");
                    if (!d.allowed()) {
                        return new NdilaRouteService.MultiStopResponse(false, d.reason(),
                                List.of(), 0, 0, null, null);
                    }
                    return new NdilaRouteService.MultiStopResponse(true, null,
                            List.of(new Coordinate(-17.82, 31.05)), 4200, 480, "CAR", "multi-stop");
                });
    }

    private String body(String path, String payload) throws Exception {
        routeServiceDefersToTheRealGuard();
        return mvc.perform(post(path)
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Actor-ID", "actor-1")
                        .header("X-Actor-Type", "SERVICE")
                        .header("X-Purpose-Of-Use", "OPERATIONS_MANAGEMENT")
                        .header("X-Correlation-ID", CORRELATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn().getResponse().getContentAsString();
    }

    private static final String MATRIX_BODY = """
            {"origins":[{"latitude":-17.82,"longitude":31.05}],
             "destinations":[{"latitude":-17.83,"longitude":31.06}],"mode":"CAR"}""";

    private static final String ROUTE_BODY = """
            {"origin":{"latitude":-17.82,"longitude":31.05},
             "destination":{"latitude":-17.83,"longitude":31.06},
             "mode":"CAR","optimizeFor":"FASTEST","avoid":[],"includeGeometry":false}""";

    private static final String MULTI_STOP_BODY = """
            {"waypoints":[{"latitude":-17.82,"longitude":31.05},
                          {"latitude":-17.83,"longitude":31.06}],
             "mode":"CAR","optimizeFor":"FASTEST"}""";

    /** The specific regression: the find-care map's distance source could never be authorized. */
    @Test
    void distanceMatrixWithActorHeadersIsNotDeniedAsUnauthenticated() throws Exception {
        String response = body("/api/v1/ndila/distance-matrix", MATRIX_BODY);

        assertThat(response)
                .as("the handler discarded X-Actor-ID/X-Actor-Type, so the guard saw no actor")
                .doesNotContain("UNAUTHENTICATED_ACTOR");
        assertThat(response).contains("\"success\":true");
    }

    @Test
    void multiStopWithActorHeadersIsNotDeniedAsUnauthenticated() throws Exception {
        String response = body("/api/v1/ndila/routes/multi-stop", MULTI_STOP_BODY);

        assertThat(response).doesNotContain("UNAUTHENTICATED_ACTOR");
        assertThat(response).contains("\"success\":true");
    }

    /** {@code eta} delegates to {@code route}, and was passing nulls across that call. */
    @Test
    void etaWithActorHeadersIsNotDeniedAsUnauthenticated() throws Exception {
        String response = body("/api/v1/ndila/eta", ROUTE_BODY);

        assertThat(response).doesNotContain("UNAUTHENTICATED_ACTOR");
        assertThat(response).contains("\"success\":true");
    }

    /** The sibling that always worked — pinned so the fix converges on it rather than away from it. */
    @Test
    void routeWithActorHeadersIsNotDeniedAsUnauthenticated() throws Exception {
        String response = body("/api/v1/ndila/routes", ROUTE_BODY);

        assertThat(response).doesNotContain("UNAUTHENTICATED_ACTOR");
        assertThat(response).contains("\"success\":true");
    }

    /**
     * The guard must still bite. Without this, a handler that hardcoded a fake actor would pass
     * every assertion above — the test would pin "never denies" instead of "forwards the caller".
     */
    @Test
    void distanceMatrixWithoutActorHeadersIsStillDenied() throws Exception {
        routeServiceDefersToTheRealGuard();

        String response = mvc.perform(post("/api/v1/ndila/distance-matrix")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Correlation-ID", CORRELATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MATRIX_BODY))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).contains("UNAUTHENTICATED_ACTOR");
    }

    /**
     * The denial the BFF was tripping over is an HTTP 200. Stated as its own assertion so nobody
     * later "fixes" a routing failure by checking the status code.
     */
    @Test
    void aGuardDenialIsReturnedAsHttpTwoHundred() throws Exception {
        routeServiceDefersToTheRealGuard();

        mvc.perform(post("/api/v1/ndila/distance-matrix")
                        .header("X-Tenant-ID", TENANT)
                        .header("X-Correlation-ID", CORRELATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MATRIX_BODY))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("the guard verdict travels in the body, not the status line")
                        .isEqualTo(200));
    }


    /**
     * Structural backstop: no routing handler may build its trust context with a hardcoded null
     * actor. This is the shape of the defect, and it recurred three times in one file.
     */
    @Test
    void noRoutingHandlerHardcodesANullActor() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/zw/gov/mohcc/impilo/ndila/api/NdilaRoutingController.java"));

        assertThat(source.replaceAll("\\s+", " "))
                .as("fromHeaders(tenantId, actorId, actorType, ...) — positions 2 and 3 are the actor")
                .doesNotContain("fromHeaders( tenantId, null, null");
    }
}
