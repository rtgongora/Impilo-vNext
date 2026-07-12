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
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

/**
 * Public gateway lane — health professionals register verification (no
 * authentication).
 *
 * <p>Anonymous visitors can confirm whether a registration number belongs to a
 * registered health professional and whether the practising certificate is
 * current. The register's public view discloses register facts only: display
 * name, profession, register status, certificate validity window and the
 * registering authority. Contact details, national identifiers and
 * disciplinary detail are never exposed. Lookup is exact-match by number; a
 * miss is a 200 with a uniform NOT_FOUND status (no enumeration oracle).
 * Governed by docs/architecture/gateway-public-lane-security-adr.md.</p>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/practitioners")
public class PublicGatewayPractitionerBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayPractitionerBffController.class);

    private final VarapiServiceClient professionalRegisterClient;

    public PublicGatewayPractitionerBffController(VarapiServiceClient professionalRegisterClient) {
        this.professionalRegisterClient = professionalRegisterClient;
    }

    @GetMapping("/verify/{registrationNumber}")
    public ResponseEntity<JsonNode> verify(@PathVariable String registrationNumber) {
        if (registrationNumber == null || registrationNumber.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        try {
            return ResponseEntity.ok(professionalRegisterClient.publicPractitionerVerify(registrationNumber.trim()));
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.warn("Public practitioner verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
