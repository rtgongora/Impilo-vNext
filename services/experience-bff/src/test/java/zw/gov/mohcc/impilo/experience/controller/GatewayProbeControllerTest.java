package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The D7 instrument exists to be denied. Everything else about it is load-bearing too —
 * if it ever started reading or writing, denying it would no longer be free, and the
 * cutover would lose the one endpoint it can afford to break on purpose.
 */
class GatewayProbeControllerTest {

    @Test
    void returnsAFixedBodyWithNoSideEffects() {
        Map<String, Object> body = new GatewayProbeController().probe();

        assertThat(body)
                .containsEntry("probe", "gateway-probe")
                .containsEntry("reachedService", true)
                .containsKey("observedAt")
                .containsKey("meaning");
        assertThat(body.get("meaning").toString())
                .contains("gateway did not deny");
    }
}
