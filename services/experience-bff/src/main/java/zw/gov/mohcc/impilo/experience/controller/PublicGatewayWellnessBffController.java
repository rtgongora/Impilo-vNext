package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.SimbaServiceClient;

import java.util.Map;

/**
 * Public wellness lane for the gateway (no authentication). Discovery + one-off use without
 * sign-in: screening &amp; immunisation-schedule definitions, and a COMPUTE-ONLY health calculator
 * (BMI, blood-pressure category) that persists nothing (doctrine §13.3 — anonymous assessments
 * retain no data). Tracking progress, saving plans, reminders and device data require sign-in.
 *
 * <ul>
 *   <li>GET  /wellness/screening-programmes — screening programme definitions (simba)</li>
 *   <li>POST /wellness/calculate            — stateless one-off calculator (simba)</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/wellness")
public class PublicGatewayWellnessBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayWellnessBffController.class);

    private final SimbaServiceClient simbaClient;

    public PublicGatewayWellnessBffController(SimbaServiceClient simbaClient) {
        this.simbaClient = simbaClient;
    }

    @GetMapping("/screening-programmes")
    public ResponseEntity<JsonNode> screeningProgrammes() {
        try {
            return ResponseEntity.ok(simbaClient.publicScreeningProgrammes());
        } catch (Exception e) {
            log.warn("Public gateway screening programmes failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @PostMapping("/calculate")
    public ResponseEntity<JsonNode> calculate(@RequestBody(required = false) Map<String, Object> body) {
        try {
            return ResponseEntity.ok(simbaClient.publicWellnessCalculate(body == null ? Map.of() : body));
        } catch (org.springframework.web.client.HttpClientErrorException.BadRequest br) {
            // Surface the service's {error:{code,message}} 400 unchanged.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(br.getResponseBodyAs(JsonNode.class));
        } catch (Exception e) {
            log.warn("Public gateway wellness calculate failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
