package zw.gov.mohcc.impilo.indawo.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import zw.gov.mohcc.impilo.indawo.api.dto.ExternalSiteResponse;
import zw.gov.mohcc.impilo.indawo.api.dto.SiteResponse;
import zw.gov.mohcc.impilo.indawo.api.dto.SiteStatusSummary;
import zw.gov.mohcc.impilo.indawo.api.dto.UpsertSiteRequest;
import zw.gov.mohcc.impilo.indawo.api.policy.SiteRepresentation;
import zw.gov.mohcc.impilo.shared.visibility.AggregateVisibilityGuard;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityHeaderParser;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;
import zw.gov.mohcc.impilo.indawo.core.SiteService;
import zw.gov.mohcc.impilo.indawo.domain.SiteEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
public class SiteController {

    private static final Logger log = LoggerFactory.getLogger(SiteController.class);
    private final SiteService siteService;
    private final ObjectMapper objectMapper;

    public SiteController(SiteService siteService, ObjectMapper objectMapper) {
        this.siteService = siteService;
        this.objectMapper = objectMapper;
    }

    // ── PUT /internal/v1/sites/{site_id} — Idempotent upsert ──

    @PutMapping("/internal/v1/sites/{site_id}")
    public ResponseEntity<?> upsertSite(
            @PathVariable("site_id") UUID siteId,
            @Valid @RequestBody UpsertSiteRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);

        log.info("Upsert site [siteId={}, name={}] correlationId={}",
                siteId, request.name(), ctx.correlationId());

        SiteService.UpsertResult result = siteService.upsertSite(
                siteId,
                UUID.fromString(ctx.tenantId()),
                ctx.podId(),
                ctx.correlationId(),
                idempotencyKey,
                request);

        SiteResponse response = toSiteResponse(result.site());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    // ── GET /internal/v1/sites/{site_id} — Full internal view ──

    @GetMapping("/internal/v1/sites/{site_id}")
    public ResponseEntity<?> getSiteInternal(@PathVariable("site_id") UUID siteId,
                                             HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        log.info("Get site [siteId={}] correlationId={}", siteId, ctx.correlationId());

        Optional<SiteEntity> site = siteService.getSite(siteId);
        if (site.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", "Site not found",
                            ctx.requestId(), ctx.correlationId()));
        }
        SiteResponse body = toSiteResponse(site.get());
        VisibilityProfile vis = VisibilityHeaderParser.parseFlat(httpRequest);
        if (AggregateVisibilityGuard.blocksRowLevelDetail(vis)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorEnvelope.of("VISIBILITY_AGGREGATE_ONLY",
                            "Site detail is not available under aggregate-only visibility.",
                            ctx.requestId(), ctx.correlationId()));
        }
        body = SiteRepresentation.apply(body, vis);
        return ResponseEntity.ok(body);
    }

    // ── GET /internal/v1/sites/{site_id}/status-summary — Lightweight legitimacy view ──

    @GetMapping("/internal/v1/sites/{site_id}/status-summary")
    public ResponseEntity<?> getSiteStatusSummary(@PathVariable("site_id") UUID siteId) {
        RequestContext ctx = RequestContextHolder.require();
        log.info("Get site status-summary [siteId={}] correlationId={}", siteId, ctx.correlationId());

        Optional<SiteEntity> site = siteService.getSite(siteId);
        if (site.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", "Site not found",
                            ctx.requestId(), ctx.correlationId()));
        }
        SiteEntity s = site.get();
        return ResponseEntity.ok(new SiteStatusSummary(
                s.getSiteId(),
                s.getSiteCode(),
                s.getName(),
                s.getStatus(),
                s.getRegulatoryStatus(),
                s.getOperationalStatus(),
                s.isActiveFlag()
        ));
    }

    // ── GET /internal/v1/sites — Paged list ──

    @GetMapping("/internal/v1/sites")
    public ResponseEntity<?> listSites(
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "20") int limit) {

        RequestContext ctx = RequestContextHolder.require();
        log.info("List sites [cursor={}, limit={}] correlationId={}", cursor, limit, ctx.correlationId());

        Page<SiteEntity> page = siteService.listSites(UUID.fromString(ctx.tenantId()), cursor, limit);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toSiteResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("total_elements", page.getTotalElements());
        body.put("has_more", page.hasNext());
        return ResponseEntity.ok(body);
    }

    // ── GET /external/v1/sites/{site_id} — Policy-filtered fields ──

    @GetMapping("/external/v1/sites/{site_id}")
    public ResponseEntity<?> getSiteExternal(@PathVariable("site_id") UUID siteId) {
        RequestContext ctx = RequestContextHolder.require();
        log.info("External get site [siteId={}] correlationId={}", siteId, ctx.correlationId());

        Optional<SiteEntity> site = siteService.getSite(siteId);
        if (site.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", "Site not found",
                            ctx.requestId(), ctx.correlationId()));
        }
        SiteEntity s = site.get();
        ExternalSiteResponse response = new ExternalSiteResponse(
                s.getSiteId(), s.getName(), s.getType(), s.getStatus());
        return ResponseEntity.ok(response);
    }

    private SiteResponse toSiteResponse(SiteEntity entity) {
        Object address = parseJsonSafe(entity.getAddressJson());
        Object geo = parseJsonSafe(entity.getGeoJson());
        return new SiteResponse(
                entity.getSiteId(),
                entity.getTenantId(),
                entity.getName(),
                entity.getType(),
                address,
                geo,
                entity.getStatus(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private Object parseJsonSafe(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }
}
