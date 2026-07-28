package zw.gov.mohcc.impilo.daidzai.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.daidzai.core.MciCasualtyService;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.MciCasualtyEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Individual casualty tracking within an MCI incident (V202, W11). */
@RestController
@RequestMapping("/internal/v1/daidzai/disasters/{incidentId}/casualties")
public class MciCasualtyController {

    private final MciCasualtyService service;

    public MciCasualtyController(MciCasualtyService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MciCasualtyEntity> tag(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID incidentId, @RequestBody Map<String, Object> body) {
        MciCasualtyEntity c = service.tagCasualty(tenantId, incidentId,
                str(body, "casualtyReference"), str(body, "sieveCategory"),
                uuid(body, "siteId"), str(body, "taggedBy"));
        return ResponseEntity.status(HttpStatus.CREATED).body(c);
    }

    @GetMapping
    public List<MciCasualtyEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId, @PathVariable UUID incidentId) {
        return service.listForIncident(tenantId, incidentId);
    }

    /** Read by PCT's bulk-mint pass. */
    @GetMapping("/unminted")
    public List<MciCasualtyEntity> unminted(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId, @PathVariable UUID incidentId) {
        return service.listUnminted(tenantId, incidentId);
    }

    @PostMapping("/{casualtyId}/status")
    public MciCasualtyEntity updateStatus(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID incidentId, @PathVariable UUID casualtyId, @RequestBody Map<String, Object> body) {
        return service.updateStatus(tenantId, casualtyId, str(body, "status"), uuid(body, "destinationFacilityId"));
    }

    /** Called back by PCT's bulk-mint pass to write the minted episode id onto this casualty. */
    @PostMapping("/{casualtyId}/link-episode")
    public MciCasualtyEntity linkEpisode(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID incidentId, @PathVariable UUID casualtyId, @RequestBody Map<String, Object> body) {
        UUID episodeId = uuid(body, "emergencyEpisodeId");
        if (episodeId == null) throw new IllegalArgumentException("emergencyEpisodeId is required");
        return service.linkEpisode(tenantId, casualtyId, episodeId);
    }

    private static String str(Map<String, Object> b, String k) {
        Object v = b.get(k); return v != null ? v.toString() : null;
    }
    private static UUID uuid(Map<String, Object> b, String k) {
        String s = str(b, k);
        if (s == null || s.isBlank()) return null;
        try { return UUID.fromString(s.trim()); } catch (IllegalArgumentException e) { return null; }
    }
}
