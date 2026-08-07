package zw.gov.mohcc.impilo.indawo.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.indawo.core.PlaceLinkService;
import zw.gov.mohcc.impilo.indawo.domain.PlaceLinkEntity;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;

/**
 * Cross-registry place links — internal API, single writer (D-L2).
 *
 * <p>Writes are steward-gated (defence-in-depth behind the ext_authz path and
 * the {@code PLACE-LINK-STEWARD} policy spec): only registry/steward capacities
 * may create or end links. Reads serve both directions so Tuso and the BFF can
 * ask "what is this facility/site linked to" without a mirror table.</p>
 */
@RestController
public class PlaceLinkController {

    /**
     * Creating or ending a cross-registry place link.
     *
     * <p>Was {@code Set.of("SYSTEM", "REGISTRY_ADMIN", "NATIONAL_ADMIN", "DATA_STEWARD")}. The last
     * three are role names no client emits, so the gate was {@code {SYSTEM}} in practice and no
     * human steward could ever pass it. They are kept as the recorded intent; see
     * {@link ActorTypeGuard.Duty}.</p>
     */
    private static final ActorTypeGuard.Duty MAINTAIN_PLACE_LINKS = new ActorTypeGuard.Duty(
            "maintaining cross-registry place links",
            ActorTypeGuard.BACK_OFFICE_WRITERS,
            Set.of("REGISTRY_ADMIN", "NATIONAL_ADMIN", "DATA_STEWARD"));

    private final PlaceLinkService placeLinkService;

    public PlaceLinkController(PlaceLinkService placeLinkService) {
        this.placeLinkService = placeLinkService;
    }

    public record CreateLinkRequest(String leftRefType, UUID leftRefId,
                                    String rightRefType, UUID rightRefId,
                                    String linkType, OffsetDateTime validFrom, OffsetDateTime validTo,
                                    String provenance) {}

    @PostMapping("/internal/v1/place-links")
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateLinkRequest request,
                                                      HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        String steward = requireSteward(http);
        PlaceLinkEntity link = placeLinkService.create(
                UUID.fromString(ctx.tenantId()), ctx.podId(), ctx.correlationId(), steward,
                new PlaceLinkService.CreateLink(
                        request.leftRefType(), request.leftRefId(),
                        request.rightRefType(), request.rightRefId(),
                        request.linkType(), request.validFrom(), request.validTo(),
                        request.provenance()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(link));
    }

    @PostMapping("/internal/v1/place-links/{id}/end")
    public ResponseEntity<Map<String, Object>> end(@PathVariable("id") UUID id, HttpServletRequest http) {
        RequestContext ctx = RequestContextHolder.require();
        String steward = requireSteward(http);
        PlaceLinkEntity link = placeLinkService.end(
                UUID.fromString(ctx.tenantId()), ctx.podId(), ctx.correlationId(), steward, id);
        return ResponseEntity.ok(toResponse(link));
    }

    /** Both-direction read: every ACTIVE link where the ref appears on either side. */
    @GetMapping("/internal/v1/place-links")
    public ResponseEntity<Map<String, Object>> byRef(@RequestParam("refType") String refType,
                                                     @RequestParam("refId") UUID refId) {
        RequestContext ctx = RequestContextHolder.require();
        List<PlaceLinkEntity> links = placeLinkService.findByRef(
                UUID.fromString(ctx.tenantId()), refType.trim().toUpperCase(Locale.ROOT), refId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("links", links.stream().map(this::toResponse).toList());
        return ResponseEntity.ok(body);
    }

    private String requireSteward(HttpServletRequest http) {
        String actorType = http.getHeader("X-Actor-Type");
        String actorId = http.getHeader("X-Actor-ID");
        String normalised = actorType == null ? "" : actorType.trim().toUpperCase(Locale.ROOT);
        ActorTypeGuard.require(actorType, http.getHeader("X-Actor-ID"), MAINTAIN_PLACE_LINKS);
        return actorId != null ? actorId : normalised;
    }

    private Map<String, Object> toResponse(PlaceLinkEntity link) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", link.getId());
        m.put("leftRefType", link.getLeftRefType());
        m.put("leftRefId", link.getLeftRefId());
        m.put("rightRefType", link.getRightRefType());
        m.put("rightRefId", link.getRightRefId());
        m.put("linkType", link.getLinkType());
        m.put("status", link.getStatus());
        m.put("validFrom", link.getValidFrom());
        m.put("validTo", link.getValidTo());
        m.put("provenance", link.getProvenance());
        return m;
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> onConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}
