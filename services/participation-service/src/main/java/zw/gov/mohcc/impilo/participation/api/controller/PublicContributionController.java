package zw.gov.mohcc.impilo.participation.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.participation.core.IdeaBoardService;
import zw.gov.mohcc.impilo.participation.core.PublicContributionIntakeService;
import zw.gov.mohcc.impilo.participation.core.TestingCohortService;
import zw.gov.mohcc.impilo.participation.persistence.entity.ContributionEntity;
import zw.gov.mohcc.impilo.participation.persistence.entity.TestingCohortEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anonymous public "Get Involved" lane (gateway-public-lane ADR). Reached only through the
 * experience-BFF public gateway controller, which enforces fail-closed abuse controls (rate
 * windows, body caps) before forwarding. Only tenant/pod/request context arrives here (Envoy
 * strips actor context on the public bypass). Responses are disclosure-limited — no
 * claim-code hashes, submitter refs, or internal metadata are ever returned.
 */
@RestController
@RequestMapping("/v1/public/participation")
public class PublicContributionController {

    private static final Logger log = LoggerFactory.getLogger(PublicContributionController.class);
    private static final int MAX_TITLE = 512;
    private static final int MAX_TEXT = 8000;
    private static final int MAX_SHORT = 255;

    private final PublicContributionIntakeService intakeService;
    private final IdeaBoardService ideaBoardService;
    private final TestingCohortService testingCohortService;

    public PublicContributionController(PublicContributionIntakeService intakeService,
                                        IdeaBoardService ideaBoardService,
                                        TestingCohortService testingCohortService) {
        this.intakeService = intakeService;
        this.ideaBoardService = ideaBoardService;
        this.testingCohortService = testingCohortService;
    }

    public record PublicContributionRequest(String type, String needArea, String title,
                                            String problemOrOpportunity, String betterExperience,
                                            String beneficiary, Boolean willingToHelp, String contact) {}

    public record SupportRequest(String supporterKey) {}

    public record EnrollRequest(String contact, String segment) {}

    @PostMapping("/contributions")
    public ResponseEntity<Map<String, Object>> submit(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody PublicContributionRequest body) {
        String title = body.title();
        String problem = body.problemOrOpportunity();
        if ((title == null || title.isBlank()) && (problem == null || problem.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A short title or a description of what could be better is required");
        }
        if ((title != null && title.length() > MAX_TITLE)
                || (problem != null && problem.length() > MAX_TEXT)
                || (body.betterExperience() != null && body.betterExperience().length() > MAX_TEXT)
                || (body.beneficiary() != null && body.beneficiary().length() > MAX_SHORT)
                || (body.needArea() != null && body.needArea().length() > MAX_SHORT)
                || (body.contact() != null && body.contact().length() > MAX_SHORT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "field length exceeded");
        }
        try {
            PublicContributionIntakeService.PublicIntakeResult result = intakeService.submitPublic(
                    tenantId, body.type(), trim(body.needArea()), trim(title), trim(problem),
                    trim(body.betterExperience()), trim(body.beneficiary()),
                    Boolean.TRUE.equals(body.willingToHelp()), body.contact());
            log.info("Public contribution received [reference={}] tenant={}", result.reference(), tenantId);
            Map<String, Object> attrs = new LinkedHashMap<>();
            attrs.put("reference", result.reference());
            attrs.put("claimCode", result.claimCode());
            attrs.put("status", result.status());
            return ResponseEntity.status(HttpStatus.CREATED).body(attrs);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/contributions/{claimCode}")
    public ResponseEntity<PublicContributionIntakeService.PublicContributionStatus> status(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable String claimCode) {
        return intakeService.statusByClaimCode(tenantId, claimCode)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown claim code"));
    }

    @GetMapping("/board")
    public ResponseEntity<List<Map<String, Object>>> board(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        List<Map<String, Object>> items = ideaBoardService.board(tenantId).stream()
                .map(PublicContributionController::toBoardItem)
                .toList();
        return ResponseEntity.ok(items);
    }

    @PostMapping("/board/{id}/support")
    public ResponseEntity<Map<String, Object>> support(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) SupportRequest body) {
        String supporterKey = body != null ? body.supporterKey() : null;
        if (supporterKey == null || supporterKey.isBlank() || supporterKey.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A supporter key is required");
        }
        ContributionEntity c = ideaBoardService.support(tenantId, id, supporterKey.trim());
        return ResponseEntity.ok(Map.of(
                "reference", c.getReference(),
                "supportCount", c.getSupportCount() == null ? 0 : c.getSupportCount()));
    }

    @GetMapping("/cohorts")
    public ResponseEntity<List<Map<String, Object>>> cohorts(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        List<Map<String, Object>> items = testingCohortService.openCohorts(tenantId).stream()
                .map(PublicContributionController::toCohortItem)
                .toList();
        return ResponseEntity.ok(items);
    }

    @PostMapping("/cohorts/{id}/enroll")
    public ResponseEntity<Map<String, Object>> enroll(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID id,
            @RequestBody(required = false) EnrollRequest body) {
        String contact = body != null ? body.contact() : null;
        if (contact == null || contact.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A contact is required so we can reach you about testing");
        }
        if (contact.length() > MAX_SHORT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contact is too long");
        }
        try {
            testingCohortService.enroll(tenantId, id, null, contact.trim());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", "ENROLLED",
                    "message", "Thank you for signing up to help test. We will be in touch."));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static Map<String, Object> toBoardItem(ContributionEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId().toString());
        m.put("reference", c.getReference());
        m.put("type", c.getType());
        m.put("needArea", c.getNeedArea());
        m.put("title", c.getTitle());
        m.put("problemOrOpportunity", c.getProblemOrOpportunity());
        m.put("betterExperience", c.getBetterExperience());
        m.put("beneficiary", c.getBeneficiary());
        m.put("status", c.getStatus());
        m.put("supportCount", c.getSupportCount() == null ? 0 : c.getSupportCount());
        m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
        return m;
    }

    private static Map<String, Object> toCohortItem(TestingCohortEntity cohort) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", cohort.getId().toString());
        m.put("name", cohort.getName());
        m.put("segment", cohort.getSegment());
        m.put("description", cohort.getDescription());
        return m;
    }

    private static String trim(String v) {
        return v == null ? null : v.trim();
    }
}
