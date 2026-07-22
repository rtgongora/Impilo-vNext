package zw.gov.mohcc.impilo.experience.khuluma;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

/**
 * Khuluma "upcoming sessions" seam — GET /internal/v1/khuluma/sessions/upcoming.
 *
 * Answers the hub's "what is happening next?" for the signed-in citizen by composing booking
 * appointments + pct telehealth (see {@link KhulumaSessionsCompositionService}). Read-only; the
 * response carries a per-source ok/error envelope so the hub degrades honestly, never invents.
 */
@RestController
@RequestMapping("/internal/v1/khuluma")
public class KhulumaSessionsController {

    private final KhulumaSessionsCompositionService composition;

    public KhulumaSessionsController(KhulumaSessionsCompositionService composition) {
        this.composition = composition;
    }

    @GetMapping("/sessions/upcoming")
    public ResponseEntity<Map<String, Object>> upcoming(
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> data = composition.upcomingFor(actorId);
        return ResponseEntity.ok(Map.of("data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
