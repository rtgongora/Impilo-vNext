package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;

/**
 * Public coverage &amp; financing lane for the gateway (no authentication). A person can compare
 * medical-aid/insurance plans and their benefits WITHOUT signing in — catalogue data only
 * (plan/benefit definitions), never personal membership, eligibility or claims. Checking your
 * own eligibility, membership or claims requires sign-in.
 *
 * <ul>
 *   <li>GET /coverage/plans                          — ACTIVE plan catalogue (coverage)</li>
 *   <li>GET /coverage/plans/{planVersionId}/benefits — a plan version's benefits (coverage)</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/coverage")
public class PublicGatewayCoverageBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayCoverageBffController.class);

    private final CoverageServiceClient coverageClient;

    public PublicGatewayCoverageBffController(CoverageServiceClient coverageClient) {
        this.coverageClient = coverageClient;
    }

    @GetMapping("/plans")
    public ResponseEntity<JsonNode> plans() {
        try {
            return ResponseEntity.ok(coverageClient.publicCoveragePlans());
        } catch (Exception e) {
            log.warn("Public gateway coverage plans failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping("/plans/{planVersionId}/benefits")
    public ResponseEntity<JsonNode> benefits(@PathVariable String planVersionId) {
        try {
            return ResponseEntity.ok(coverageClient.publicPlanBenefits(planVersionId));
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.warn("Public gateway coverage benefits failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
