package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.MsikaFlowServiceClient;
import zw.gov.mohcc.impilo.experience.client.MsikaServiceClient;

/**
 * Public marketplace browse lane for the gateway (no authentication). A person can browse
 * PUBLISHED health-product/service listings and the approved-vendor directory WITHOUT
 * signing in — allow-listed catalogue facts only (title, summary, indicative price,
 * fulfilment mode, verification badge); no seller PII, no order data. Ordering, paying,
 * submitting a prescription, tracking delivery or managing a supplier account require
 * sign-in (a guest basket is held client-side and claimed at sign-in — PD-7).
 *
 * <ul>
 *   <li>GET /marketplace/listings?q=&amp;category=&amp;risk=&amp;page=&amp;size= — browse/search (msika)</li>
 *   <li>GET /marketplace/listings/{listingId}                          — one listing (msika)</li>
 *   <li>GET /marketplace/vendors?page=&amp;size=                          — approved vendors (msika-flow)</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/marketplace")
public class PublicGatewayMarketplaceBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayMarketplaceBffController.class);

    private final MsikaServiceClient msikaClient;
    private final MsikaFlowServiceClient msikaFlowClient;

    public PublicGatewayMarketplaceBffController(MsikaServiceClient msikaClient,
                                                 MsikaFlowServiceClient msikaFlowClient) {
        this.msikaClient = msikaClient;
        this.msikaFlowClient = msikaFlowClient;
    }

    @GetMapping("/listings")
    public ResponseEntity<JsonNode> listings(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String risk,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            if (q != null && !q.isBlank()) params.add("q", q);
            if (category != null && !category.isBlank()) params.add("category", category);
            if (risk != null && !risk.isBlank()) params.add("risk", risk);
            params.add("page", String.valueOf(page));
            params.add("size", String.valueOf(size));
            return ResponseEntity.ok(msikaClient.publicListings(params));
        } catch (Exception e) {
            log.warn("Public gateway marketplace listings failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping("/listings/{listingId}")
    public ResponseEntity<JsonNode> listing(@PathVariable String listingId) {
        try {
            JsonNode body = msikaClient.publicListing(listingId);
            if (body == null || body.isNull()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(body);
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.warn("Public gateway marketplace listing detail failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping("/vendors")
    public ResponseEntity<JsonNode> vendors(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("page", String.valueOf(page));
            params.add("size", String.valueOf(size));
            return ResponseEntity.ok(msikaFlowClient.publicVendors(params));
        } catch (Exception e) {
            log.warn("Public gateway marketplace vendors failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
