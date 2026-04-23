package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityHeaderParser;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Surfaces the effective visibility profile propagated from Tshepo via gateway headers.
 */
@RestController
@RequestMapping("/internal/v1/profile")
public class VisibilityProfileController {

    private final ObjectMapper objectMapper;

    public VisibilityProfileController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping("/visibility")
    public ResponseEntity<Map<String, Object>> currentVisibility(HttpServletRequest request) {
        String obligations = request.getHeader(TrustHeaders.OBLIGATIONS);
        VisibilityProfile p = VisibilityHeaderParser.resolve(request, objectMapper);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("obligationsHeaderPresent", obligations != null && !obligations.isBlank());
        if (p == null) {
            body.put("visibilityProfile", null);
            body.put("source", "headers-absent");
            return ResponseEntity.ok(body);
        }
        body.put("visibilityTier", p.visibilityTier());
        body.put("piiAccess", p.piiAccess());
        body.put("clinicalAccess", p.clinicalAccess());
        body.put("aggregateOnly", p.aggregateOnly());
        body.put("resourceSensitivityClass", p.resourceSensitivityClass());
        body.put("escalationGrantId", p.escalationGrantId());
        body.put("exportPolicy", p.exportPolicy());
        body.put("drillDownAllowed", p.drillDownAllowed());
        body.put("suppressFields", p.suppressFields());
        body.put("source", obligations != null && !obligations.isBlank() ? "obligations-or-headers" : "headers");
        return ResponseEntity.ok(body);
    }
}
