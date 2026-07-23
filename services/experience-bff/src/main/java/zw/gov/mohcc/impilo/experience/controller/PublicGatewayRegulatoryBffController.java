package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

/**
 * Public regulatory &amp; professional information lane for the gateway (no authentication).
 * Thin proxy over the service-side Public* controllers — the "what you need to apply"
 * reference data a person can read before starting any application: facility licensing
 * requirements/fees/classifications/rules (TUSO) and professional councils + registers
 * (VARAPI). Reference data only — no PII, no application state; starting or managing an
 * actual application requires sign-in.
 *
 * <ul>
 *   <li>GET /regulatory/requirements                    — facility licensing reference (tuso)</li>
 *   <li>GET /regulatory/councils                        — professional councils (varapi)</li>
 *   <li>GET /regulatory/councils/{councilCode}/registers — a council's registers (varapi)</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/regulatory")
public class PublicGatewayRegulatoryBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayRegulatoryBffController.class);

    private final TusoServiceClient tusoClient;
    private final VarapiServiceClient varapiClient;

    public PublicGatewayRegulatoryBffController(TusoServiceClient tusoClient,
                                                VarapiServiceClient varapiClient) {
        this.tusoClient = tusoClient;
        this.varapiClient = varapiClient;
    }

    @GetMapping("/requirements")
    public ResponseEntity<JsonNode> requirements() {
        try {
            return ResponseEntity.ok(tusoClient.publicRegulatoryRequirements());
        } catch (Exception e) {
            log.warn("Public gateway regulatory requirements failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping("/councils")
    public ResponseEntity<JsonNode> councils() {
        try {
            return ResponseEntity.ok(varapiClient.publicCouncils());
        } catch (Exception e) {
            log.warn("Public gateway councils failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping("/councils/{councilCode}/registers")
    public ResponseEntity<JsonNode> councilRegisters(@PathVariable String councilCode) {
        try {
            return ResponseEntity.ok(varapiClient.publicCouncilRegisters(councilCode));
        } catch (Exception e) {
            log.warn("Public gateway council registers failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
