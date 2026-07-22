package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

/**
 * Proxies "My Regulatory Affairs" (ROM-W3) to varapi. The signed-in person's Health-ID rides the
 * forwarded trust context, so varapi resolves the caller's OWN record — never another person's.
 */
@RestController
@RequestMapping("/internal/v1/me/regulatory")
public class MeRegulatoryBffController {

    private final VarapiServiceClient varapiClient;

    public MeRegulatoryBffController(VarapiServiceClient varapiClient) {
        this.varapiClient = varapiClient;
    }

    @GetMapping("/summary")
    public ResponseEntity<JsonNode> summary() {
        return ResponseEntity.ok(varapiClient.getMyRegulatorySummary());
    }
}
