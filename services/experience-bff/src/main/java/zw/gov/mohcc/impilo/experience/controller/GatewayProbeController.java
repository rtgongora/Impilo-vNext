package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * A route that exists to be denied.
 *
 * <p>The D7 cutover turns on the gateway's {@code ext_authz} filter. The question that flip
 * has to answer is narrow and easy to get wrong: <em>is the PDP actually on the request
 * path?</em> Every other signal is ambiguous. A 200 proves nothing, because a request that
 * never reaches the PDP also returns 200. Policy-decision logs prove the PDP is being
 * called by something, not that it can refuse this path. A 403 on a real endpoint proves
 * the mechanism, at the cost of denying real work to do it.</p>
 *
 * <p>So this endpoint returns a fixed body, reads nothing, writes nothing, and carries no
 * data worth protecting. Paired with the DENY rule seeded inactive in V060, activating that
 * rule turns this route's 200 into a 403 and nothing else in the estate changes. If it stays
 * 200, the gateway is not consulting the PDP for this path — which is the single fact the
 * cutover's first flip needs, obtainable in one curl and reversible by deactivating one row.
 * See docs/runbooks/work-context-d7-cutover.md.</p>
 *
 * <p>It is deliberately not an actuator endpoint: probes are exempted from enforcement so a
 * failing PDP cannot take pods down, so an actuator path would be excluded from the very
 * mechanism under test and would report success no matter what.</p>
 */
@RestController
@RequestMapping("/internal/v1/gateway-probe")
public class GatewayProbeController {

    @GetMapping
    public Map<String, Object> probe() {
        return Map.of(
                "probe", "gateway-probe",
                "reachedService", true,
                "meaning", "This response came from the service, so the gateway did not deny it.",
                "observedAt", Instant.now().toString());
    }
}
