package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.Map;

/**
 * Public facility-certificate verification (no authentication): forwards the
 * disclosure-limited verdict from the facility register. Anyone can scan a certificate code and
 * see facility name/location, certificate number, authority, dates, status and
 * public conditions — never findings, practitioner data or enforcement detail.
 */
@RestController
@RequestMapping("/internal/v1/public/facility-certificates")
public class PublicCertificateVerificationBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicCertificateVerificationBffController.class);

    private final TusoServiceClient tusoClient;

    public PublicCertificateVerificationBffController(TusoServiceClient tusoClient) {
        this.tusoClient = tusoClient;
    }

    @GetMapping("/verify/{code}")
    public ResponseEntity<JsonNode> verify(@PathVariable String code) {
        try {
            return ResponseEntity.ok(tusoClient.verifyCertificatePublic(code));
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.warn("Public certificate verification failed for code {}: {}", code, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    /**
     * Scan a facility QR credential (FJ QR/D-L7). The signed QR content (or a
     * typed verification code) travels in the body. Returns the policy-filtered
     * public projection or a uniform NOT_RECOGNISED shape (no oracle). Distinct
     * from the HPA certificate verify above.
     */
    @PostMapping("/credential-scan")
    public ResponseEntity<JsonNode> credentialScan(@RequestBody Map<String, String> body) {
        String qr = body == null ? null : body.getOrDefault("qr", body.get("code"));
        try {
            return ResponseEntity.ok(tusoClient.publicCredentialScan(qr));
        } catch (Exception e) {
            log.warn("Public facility credential scan failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
