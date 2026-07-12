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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public gateway lane — facility directory (no authentication).
 *
 * <p>Anonymous visitors can search the national facility register and view
 * disclosure-limited facility profiles: name, type, level, district, province,
 * status and public capabilities only. Contact persons, internal identifiers
 * and operational detail are excluded by the register's public view.
 * Governed by docs/architecture/gateway-public-lane-security-adr.md.</p>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/facilities")
public class PublicGatewayFacilityBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayFacilityBffController.class);
    private static final int MAX_PAGE_SIZE = 50;

    private final TusoServiceClient facilityRegisterClient;

    public PublicGatewayFacilityBffController(TusoServiceClient facilityRegisterClient) {
        this.facilityRegisterClient = facilityRegisterClient;
    }

    @GetMapping("/search")
    public ResponseEntity<JsonNode> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String facilityType,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Map<String, String> params = new LinkedHashMap<>();
        putIfPresent(params, "query", query);
        putIfPresent(params, "facilityType", facilityType);
        putIfPresent(params, "province", province);
        putIfPresent(params, "district", district);
        putIfPresent(params, "level", level);
        params.put("page", String.valueOf(Math.max(0, page)));
        params.put("size", String.valueOf(Math.min(Math.max(1, size), MAX_PAGE_SIZE)));
        try {
            return ResponseEntity.ok(facilityRegisterClient.publicFacilitySearch(params));
        } catch (Exception e) {
            log.warn("Public facility search failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<JsonNode> profile(@PathVariable long id) {
        try {
            return ResponseEntity.ok(facilityRegisterClient.publicFacilityProfile(id));
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.warn("Public facility profile failed for id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private static void putIfPresent(Map<String, String> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value.trim());
        }
    }
}
