package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Citizen-facing COSTA billing (mobile). Lets a citizen see what they owe.
 *
 * <p>Closes the citizen finance dead-route gap documented in
 * {@code docs/implementation/mobile-costa-bff-contract.md}. The BFF holds no state — it composes
 * the citizen's own outstanding bills from the sovereign COSTA service, actor-scoped from the
 * trust headers (the authenticated Health ID / CPID). No fabricated charges: the real
 * {@code OutstandingBillDto} list is passed through under {@code data}, or an empty list when the
 * citizen has nothing outstanding or is not yet linked to a patient account.
 */
@RestController
public class CitizenCostaController {

    private final CostaServiceClient costaClient;

    public CitizenCostaController(CostaServiceClient costaClient) {
        this.costaClient = costaClient;
    }

    @GetMapping("/internal/v1/mobile/citizen/costa/charges/pending")
    public ResponseEntity<Map<String, Object>> pendingCharges(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId) {
        Object charges;
        try {
            JsonNode outstanding = costaClient.getFinancePatientOutstanding(actorId);
            charges = (outstanding != null && !outstanding.isNull()) ? outstanding : List.of();
        } catch (Exception e) {
            // COSTA unavailable or actor not linked to a patient account — fail soft to empty,
            // never fabricate charges. Mobile renders an honest empty state.
            charges = List.of();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", charges);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }
}
