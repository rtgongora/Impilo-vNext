package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.MadiServiceClient;

/**
 * Public blood-donation appeals lane for the gateway (no authentication). A person can see
 * current blood-donation drives — an emergency/disaster-adjacent public capability
 * (doctrine §13.5) — without signing in. Allow-listed drive facts only (title, description,
 * type, location, jurisdiction, window, capacity); no donor PII. Registering as a donor or
 * booking a slot requires sign-in.
 *
 * <ul>
 *   <li>GET /blood/drives — current public donation drives/appeals (madi)</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/blood")
public class PublicGatewayBloodBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayBloodBffController.class);

    private final MadiServiceClient madiClient;

    public PublicGatewayBloodBffController(MadiServiceClient madiClient) {
        this.madiClient = madiClient;
    }

    @GetMapping("/drives")
    public ResponseEntity<JsonNode> drives() {
        try {
            return ResponseEntity.ok(madiClient.publicDrives());
        } catch (Exception e) {
            log.warn("Public gateway blood drives failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
