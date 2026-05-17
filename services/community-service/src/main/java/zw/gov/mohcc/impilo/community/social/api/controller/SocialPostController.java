package zw.gov.mohcc.impilo.community.social.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.community.social.api.dto.SocialDtos;
import zw.gov.mohcc.impilo.community.social.core.SocialService;

import java.util.UUID;

/**
 * Social timeline / posts REST API.
 *
 * Lives under {@code /internal/v1/community/social/**} so it does not
 * collide with the CHW community endpoints exposed by
 * {@link zw.gov.mohcc.impilo.community.api.controller.CommunityUnitController}
 * and friends.
 */
@RestController
@RequestMapping("/internal/v1/community/social")
public class SocialPostController {

    private static final String DEFAULT_TENANT = "00000000-0000-0000-0000-000000000001";

    private final SocialService service;

    public SocialPostController(SocialService service) {
        this.service = service;
    }

    // ── Feed ───────────────────────────────────────────────────────────────
    @GetMapping("/feed")
    public ResponseEntity<SocialDtos.FeedPageDto> feed(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestParam(defaultValue = "home") String scope,
            @RequestParam(required = false) String scopeId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok(service.feed(tenant(tenantHeader), actorId, scope, scopeId, limit, offset));
    }

    // ── Posts ──────────────────────────────────────────────────────────────
    @PostMapping("/posts")
    public ResponseEntity<SocialDtos.PostDto> createPost(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Display", required = false) String actorDisplay,
            @RequestHeader(value = "X-Actor-Role-Badge", required = false) String roleBadge,
            @RequestBody SocialDtos.CreatePostRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.createPost(tenant(tenantHeader), actorId, actorDisplay, roleBadge, req));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<SocialDtos.PostDto> getPost(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.getPost(tenant(tenantHeader), actorId, id));
    }

    @PatchMapping("/posts/{id}")
    public ResponseEntity<SocialDtos.PostDto> updatePost(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id,
            @RequestBody SocialDtos.UpdatePostRequest req) {
        return ResponseEntity.ok(service.updatePost(tenant(tenantHeader), actorId, id, req));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> archive(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id) {
        service.archivePost(tenant(tenantHeader), actorId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{id}/publish")
    public ResponseEntity<SocialDtos.PostDto> publish(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.publishDraft(tenant(tenantHeader), actorId, id));
    }

    @PostMapping("/posts/{id}/pin")
    public ResponseEntity<Void> pin(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean pinned) {
        service.pin(tenant(tenantHeader), actorId, id, pinned);
        return ResponseEntity.ok().build();
    }

    // ── Reactions, comments, bookmarks, reports ──────────────────────────
    @PostMapping("/posts/{id}/reactions")
    public ResponseEntity<SocialDtos.ReactionSummaryDto> react(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id,
            @RequestBody SocialDtos.ReactionRequest req) {
        return ResponseEntity.ok(service.react(tenant(tenantHeader), actorId, id, req.type()));
    }

    @DeleteMapping("/posts/{id}/reactions")
    public ResponseEntity<SocialDtos.ReactionSummaryDto> unreact(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.unreact(tenant(tenantHeader), actorId, id));
    }

    @GetMapping("/posts/{id}/comments")
    public ResponseEntity<?> listComments(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.listComments(tenant(tenantHeader), id));
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<SocialDtos.CommentDto> addComment(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Display", required = false) String actorDisplay,
            @PathVariable UUID id,
            @RequestBody SocialDtos.CommentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.addComment(tenant(tenantHeader), actorId, actorDisplay, id, req));
    }

    @PostMapping("/posts/{id}/bookmark")
    public ResponseEntity<?> bookmark(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id) {
        boolean state = service.toggleBookmark(tenant(tenantHeader), actorId, id, true);
        return ResponseEntity.ok(java.util.Map.of("bookmarked", state));
    }

    @DeleteMapping("/posts/{id}/bookmark")
    public ResponseEntity<?> unbookmark(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id) {
        boolean state = service.toggleBookmark(tenant(tenantHeader), actorId, id, false);
        return ResponseEntity.ok(java.util.Map.of("bookmarked", state));
    }

    @PostMapping("/posts/{id}/report")
    public ResponseEntity<SocialDtos.ModerationCaseDto> report(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantHeader,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @PathVariable UUID id,
            @RequestBody SocialDtos.ReportRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                service.reportPost(tenant(tenantHeader), actorId, id, req));
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
