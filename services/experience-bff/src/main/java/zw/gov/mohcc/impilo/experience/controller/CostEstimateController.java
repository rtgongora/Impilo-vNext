package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;

/**
 * Composed patient cost estimate — the Ruvimbo cost-estimator seam.
 *
 * COSTA owns price, coverage owns payability: this endpoint sources a service's effective
 * price from COSTA's tariff schedule, resolves the caller's active coverage, and runs the
 * coverage liability rules over that price to return the expected patient responsibility.
 * It never invents a price — an unpriced service or absent coverage is reported honestly.
 */
@RestController
@RequestMapping("/internal/v1/coverage")
public class CostEstimateController {

    private final CostaServiceClient costaClient;
    private final CoverageServiceClient coverageClient;

    public CostEstimateController(CostaServiceClient costaClient, CoverageServiceClient coverageClient) {
        this.costaClient = costaClient;
        this.coverageClient = coverageClient;
    }

    @GetMapping("/cost-estimate")
    public ResponseEntity<Map<String, Object>> estimate(
            @RequestParam("member_cpid") String memberCpid,
            @RequestParam("service_code") String serviceCode,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        Map<String, Object> meta = Map.of("request_id", requestId, "correlation_id", correlationId);
        try {
            JsonNode tariff = costaClient.getTariffByCode(serviceCode);
            if (tariff == null || !tariff.hasNonNull("price")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", Map.of("code", "TARIFF_NOT_FOUND", "message", "No active price for " + serviceCode),
                        "meta", meta));
            }
            BigDecimal price = tariff.get("price").decimalValue();
            String currency = tariff.hasNonNull("currency") ? tariff.get("currency").asText() : "USD";

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("serviceCode", serviceCode);
            data.put("price", price);
            data.put("currency", currency);
            data.put("description", tariff.hasNonNull("description") ? tariff.get("description").asText() : null);

            String coverageId = firstCoverageId(coverageClient.listPlansForMember(memberCpid));
            if (coverageId == null) {
                // Honest: no coverage means the full charge is the patient's responsibility.
                data.put("covered", false);
                data.put("patientResponsibility", price);
                data.put("message", "No active coverage found — the full charge is the patient's responsibility.");
            } else {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("coverageId", coverageId);
                body.put("standardCharge", price);
                data.put("covered", true);
                data.put("estimate", coverageClient.createLiabilityEstimate(body));
            }
            return ResponseEntity.ok(Map.of("data", data, "meta", meta));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "COST_ESTIMATE_FAILED", "message", String.valueOf(e.getMessage())),
                    "meta", meta));
        }
    }

    /** The member's active coverage id from their plan list (extractData already unwraps .data). */
    private String firstCoverageId(JsonNode plans) {
        JsonNode arr = (plans != null && plans.has("data")) ? plans.get("data") : plans;
        if (arr != null && arr.isArray() && arr.size() > 0 && arr.get(0).hasNonNull("id")) {
            return arr.get(0).get("id").asText();
        }
        return null;
    }
}
