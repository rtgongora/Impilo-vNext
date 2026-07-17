package zw.gov.mohcc.impilo.participation.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.participation.core.ContributionService;
import zw.gov.mohcc.impilo.participation.core.IdeaBoardService;
import zw.gov.mohcc.impilo.participation.core.TestingCohortService;
import zw.gov.mohcc.impilo.participation.persistence.entity.ContributionEntity;
import zw.gov.mohcc.impilo.participation.persistence.entity.ContributionEventEntity;
import zw.gov.mohcc.impilo.participation.persistence.entity.TestingCohortEntity;
import zw.gov.mohcc.impilo.participation.persistence.entity.TestingEnrollmentEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authenticated participation management lane (reached through the experience-BFF with the
 * caller's trust context). Contribution CRUD/list/status/moderation/merge/roadmap linkage,
 * plus cohort administration and authenticated support.
 */
@RestController
@RequestMapping("/internal/v1/participation")
public class ContributionController {

    private final ContributionService contributionService;
    private final IdeaBoardService ideaBoardService;
    private final TestingCohortService testingCohortService;

    public ContributionController(ContributionService contributionService,
                                  IdeaBoardService ideaBoardService,
                                  TestingCohortService testingCohortService) {
        this.contributionService = contributionService;
        this.ideaBoardService = ideaBoardService;
        this.testingCohortService = testingCohortService;
    }

    // ── Contributions ────────────────────────────────────────────────
    @GetMapping("/contributions")
    public List<ContributionEntity> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {
        return contributionService.list(tenantId, status, type);
    }

    @PostMapping("/contributions")
    public ResponseEntity<ContributionEntity> create(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = body.get("metadata") instanceof Map
                ? (Map<String, Object>) body.get("metadata") : Map.of();
        ContributionService.NewContribution cmd = new ContributionService.NewContribution(
                str(body, "type"),
                str(body, "needArea"),
                str(body, "title"),
                str(body, "problemOrOpportunity"),
                str(body, "betterExperience"),
                str(body, "beneficiary"),
                Boolean.parseBoolean(String.valueOf(body.getOrDefault("willingToHelp", "false"))),
                "AUTHENTICATED",
                actorId,
                metadata);
        ContributionEntity c = contributionService.create(tenantId, cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(c);
    }

    @GetMapping("/contributions/{id}")
    public ContributionEntity get(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        return contributionService.get(tenantId, id);
    }

    @GetMapping("/contributions/{id}/timeline")
    public List<ContributionEventEntity> timeline(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id) {
        return contributionService.timeline(tenantId, id);
    }

    @PostMapping("/contributions/{id}/transition")
    public ContributionEntity transition(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        String toStatus = require(str(body, "toStatus"), "toStatus");
        return contributionService.transition(tenantId, id, toStatus, str(body, "note"), actorId);
    }

    @PostMapping("/contributions/{id}/moderate")
    public ContributionEntity moderate(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        String state = require(str(body, "moderationState"), "moderationState");
        return contributionService.moderate(tenantId, id, state, str(body, "note"), actorId);
    }

    @PostMapping("/contributions/{id}/merge")
    public ContributionEntity merge(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        UUID targetId = uuid(body, "targetId");
        return contributionService.merge(tenantId, id, targetId, str(body, "note"), actorId);
    }

    @PostMapping("/contributions/{id}/roadmap-link")
    public ContributionEntity roadmapLink(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        String roadmapRef = require(str(body, "roadmapRef"), "roadmapRef");
        return contributionService.linkRoadmap(tenantId, id, roadmapRef, str(body, "note"), actorId);
    }

    @PostMapping("/contributions/{id}/support")
    public ContributionEntity support(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id) {
        String supporterKey = require(actorId, "actor");
        return ideaBoardService.support(tenantId, id, supporterKey);
    }

    // ── Idea board (moderation view) ──────────────────────────────────
    @GetMapping("/board")
    public List<ContributionEntity> board(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return ideaBoardService.board(tenantId);
    }

    // ── Testing cohorts ───────────────────────────────────────────────
    @GetMapping("/cohorts")
    public List<TestingCohortEntity> cohorts(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        return testingCohortService.allCohorts(tenantId);
    }

    @PostMapping("/cohorts/{id}/enroll")
    public ResponseEntity<TestingEnrollmentEntity> enroll(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, Object> body) {
        String contact = body != null ? str(body, "contact") : null;
        TestingEnrollmentEntity e = testingCohortService.enroll(tenantId, id, actorId, contact);
        return ResponseEntity.status(HttpStatus.CREATED).body(e);
    }

    // ── helpers ───────────────────────────────────────────────────────
    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private static UUID uuid(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(v.toString());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID for " + key);
        }
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing required field: " + field);
        }
        return value;
    }
}
