package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates coverage-service mesh eligibility with COSTA estimate for registration / counselling UIs.
 */
@RestController
@RequestMapping("/internal/v1/registry/coverage")
public class CoverageRegistrationPreviewController {

    private static final Logger log = LoggerFactory.getLogger(CoverageRegistrationPreviewController.class);

    private final CoverageServiceClient coverageClient;
    private final CostaServiceClient costaClient;

    public CoverageRegistrationPreviewController(CoverageServiceClient coverageClient,
                                                 CostaServiceClient costaClient) {
        this.coverageClient = coverageClient;
        this.costaClient = costaClient;
    }

    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        Map<String, Object> out = new LinkedHashMap<>();
        String patientCpid = str(body.get("patient_cpid"));
        String planCode = str(body.get("plan_code"));
        String serviceCode = str(body.get("service_code"));

        if (patientCpid != null && !patientCpid.isBlank() && planCode != null && !planCode.isBlank()) {
            try {
                Map<String, Object> eligBody = new LinkedHashMap<>();
                eligBody.put("patient_cpid", patientCpid);
                eligBody.put("plan_code", planCode);
                eligBody.put("service_code", serviceCode != null ? serviceCode : "");
                JsonNode elig = coverageClient.meshInternalEligibilityCheck(eligBody);
                out.put("eligibility", elig);
            } catch (Exception e) {
                log.warn("Eligibility preview failed: {}", e.getMessage());
                out.put("eligibility", Map.of("error", e.getMessage()));
            }
        } else {
            out.put("eligibility", Map.of("skipped", true, "reason", "patient_cpid and plan_code required for mesh check"));
        }

        try {
            Map<String, Object> estBody = buildEstimateRequest(body);
            JsonNode est = costaClient.createEstimate(estBody);
            out.put("estimate", est);
        } catch (Exception e) {
            log.warn("COSTA estimate preview failed: {}", e.getMessage());
            out.put("estimate", Map.of("error", e.getMessage()));
        }

        return ResponseEntity.ok(Map.of(
                "data", out,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildEstimateRequest(Map<String, Object> body) {
        Map<String, Object> est = new LinkedHashMap<>();
        if (body.get("exemptionCategory") != null) {
            est.put("exemptionCategory", body.get("exemptionCategory"));
        }
        if (body.get("insurancePlanId") != null) {
            est.put("insurancePlanId", body.get("insurancePlanId"));
        }
        if (body.get("context") instanceof Map<?, ?> ctx) {
            est.put("context", ctx);
        }
        List<Map<String, Object>> items = new ArrayList<>();
        Object rawItems = body.get("items");
        if (rawItems instanceof List<?> list && !list.isEmpty()) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    items.add((Map<String, Object>) m);
                }
            }
        }
        if (items.isEmpty()) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("msikaCode", body.getOrDefault("msika_code", "GEN-CONSULT"));
            line.put("description", body.getOrDefault("line_description", "Consult preview"));
            line.put("kind", "SERVICE");
            line.put("qty", new BigDecimal("1"));
            if (body.get("cost_method") != null) {
                line.put("costMethod", body.get("cost_method"));
            }
            items.add(line);
        }
        est.put("items", items);
        return est;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString().trim();
    }
}
