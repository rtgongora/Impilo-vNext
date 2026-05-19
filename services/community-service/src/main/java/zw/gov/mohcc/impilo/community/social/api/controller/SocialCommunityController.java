package zw.gov.mohcc.impilo.community.social.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.community.social.api.dto.SocialDtos;
import zw.gov.mohcc.impilo.community.social.core.SocialService;

import java.util.UUID;

/** Communities + Groups + Pages REST API for the social subdomain. */
@RestController
@RequestMapping("/internal/v1/community/social")
public class SocialCommunityController {

    private static final String DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";

    private final SocialService service;

    public SocialCommunityController(SocialService service) {
        this.service = service;
    }

    // ── Communities ───────────────────────────────────────────────────────
    @GetMapping("/communities")
    public ResponseEntity<?> listCommunities(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(service.listCommunities(tenant(tenantHeader), actorId, category, q));
    }

    @GetMapping("/communities/{id}")
    public ResponseEntity<SocialDtos.CommunityDto> getCommunity(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.getCommunity(tenant(tenantHeader), actorId, id));
    }

    @PostMapping("/communities")
    public ResponseEntity<SocialDtos.CommunityDto> createCommunity(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestBody SocialDtos.CreateCommunityRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.createCommunity(tenant(tenantHeader), actorId, req));
    }

    @PostMapping("/communities/{id}/join")
    public ResponseEntity<?> joinCommunity(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id) {
        service.joinCommunity(tenant(tenantHeader), actorId, id);
        return ResponseEntity.ok(java.util.Map.of("joined", true));
    }

    @PostMapping("/communities/{id}/leave")
    public ResponseEntity<?> leaveCommunity(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id) {
        service.leaveCommunity(tenant(tenantHeader), actorId, id);
        return ResponseEntity.ok(java.util.Map.of("joined", false));
    }

    // ── Groups ────────────────────────────────────────────────────────────
    @GetMapping("/groups")
    public ResponseEntity<?> listGroups(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestParam(required = false) UUID communityId) {
        return ResponseEntity.ok(service.listGroups(tenant(tenantHeader), actorId, communityId));
    }

    @GetMapping("/groups/{id}")
    public ResponseEntity<SocialDtos.GroupDto> getGroup(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.getGroup(tenant(tenantHeader), actorId, id));
    }

    @PostMapping("/groups")
    public ResponseEntity<SocialDtos.GroupDto> createGroup(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestBody SocialDtos.CreateGroupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.createGroup(tenant(tenantHeader), actorId, req));
    }

    @PostMapping("/groups/{id}/join")
    public ResponseEntity<?> joinGroup(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id) {
        service.joinGroup(tenant(tenantHeader), actorId, id);
        return ResponseEntity.ok(java.util.Map.of("joined", true));
    }

    // ── Pages ─────────────────────────────────────────────────────────────
    @GetMapping("/pages")
    public ResponseEntity<?> listPages(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestParam(required = false) String kind) {
        return ResponseEntity.ok(service.listPages(tenant(tenantHeader), actorId, kind));
    }

    @GetMapping("/pages/{id}")
    public ResponseEntity<SocialDtos.PageDto> getPage(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.getPage(tenant(tenantHeader), actorId, id));
    }

    @PostMapping("/pages")
    public ResponseEntity<SocialDtos.PageDto> createPage(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestBody SocialDtos.CreatePageRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.createPage(tenant(tenantHeader), actorId, req));
    }

    @PostMapping("/pages/{id}/follow")
    public ResponseEntity<?> followPage(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id) {
        boolean follows = service.followPage(tenant(tenantHeader), actorId, id);
        return ResponseEntity.ok(java.util.Map.of("following", follows));
    }

    // ── Suggestions + Moderation ─────────────────────────────────────────
    @GetMapping("/suggestions")
    public ResponseEntity<SocialDtos.SuggestionsBundleDto> suggestions(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId) {
        return ResponseEntity.ok(service.suggestions(tenant(tenantHeader), actorId));
    }

    @GetMapping("/moderation/queue")
    public ResponseEntity<?> moderationQueue(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader) {
        return ResponseEntity.ok(service.openCases(tenant(tenantHeader)));
    }

    @PostMapping("/moderation/{caseId}/resolve")
    public ResponseEntity<?> resolve(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID caseId,
            @RequestBody SocialDtos.ResolveModerationRequest req) {
        service.resolveCase(tenant(tenantHeader), actorId, caseId, req);
        return ResponseEntity.ok(java.util.Map.of("status", "RESOLVED"));
    }

    private static UUID tenant(String header) {
        if (header == null || header.isBlank()) return UUID.fromString(DEFAULT_TENANT);
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException e) {
            return UUID.fromString(DEFAULT_TENANT);
        }
    }
}
