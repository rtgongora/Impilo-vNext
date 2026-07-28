package zw.gov.mohcc.impilo.surv.api;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.surv.core.CaseInvestigationService;

/**
 * Case investigation, contact tracing and the line list.
 *
 * <p>Backs three controls that existed on the surveillance panel with no handler: "Save Update",
 * "Link Contacts" and "Generate Line List". Before this, a public-health officer could click
 * "Link Contacts" mid-outbreak, get no error, and conclude tracing had begun.
 */
@RestController
@RequestMapping("/internal/v1/cases")
public class CaseInvestigationController {

    private static final Logger log = LoggerFactory.getLogger(CaseInvestigationController.class);

    private final CaseInvestigationService service;

    public CaseInvestigationController(CaseInvestigationService service) {
        this.service = service;
    }

    public record UpdateRequest(String classification, String outcome, String labResult, String notes) {}

    public record ContactRequest(String contactName, String contactPhone, String contactCpid,
                                 String relationship, String exposureDate, String notes) {}

    public record TraceStatusRequest(String traceStatus) {}

    @PostMapping("/{caseId}/updates")
    public ResponseEntity<?> recordUpdate(@PathVariable Long caseId,
                                         @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
                                         @RequestBody UpdateRequest body) {
        RequestContext ctx = RequestContextHolder.require();
        if (actorId == null || actorId.isBlank()) {
            return missingActor();
        }
        log.info("Case investigation update [caseId={}] correlationId={}", caseId, ctx.correlationId());
        try {
            var saved = service.recordUpdate(UUID.fromString(ctx.tenantId()), caseId,
                    body.classification(), body.outcome(), body.labResult(), body.notes(),
                    actorId);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", saved));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @GetMapping("/{caseId}/updates")
    public ResponseEntity<?> listUpdates(@PathVariable Long caseId) {
        RequestContext ctx = RequestContextHolder.require();
        return ResponseEntity.ok(Map.of(
                "items", service.updates(UUID.fromString(ctx.tenantId()), caseId)));
    }

    @PostMapping("/{caseId}/contacts")
    public ResponseEntity<?> linkContact(@PathVariable Long caseId,
                                        @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
                                        @RequestBody ContactRequest body) {
        RequestContext ctx = RequestContextHolder.require();
        if (actorId == null || actorId.isBlank()) {
            return missingActor();
        }
        log.info("Link contact to case [caseId={}] correlationId={}", caseId, ctx.correlationId());
        try {
            LocalDate exposure = body.exposureDate() == null || body.exposureDate().isBlank()
                    ? null : LocalDate.parse(body.exposureDate());
            var saved = service.linkContact(UUID.fromString(ctx.tenantId()), caseId,
                    body.contactName(), body.contactPhone(), body.contactCpid(),
                    body.relationship(), exposure, body.notes(), actorId);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", saved));
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
            return badRequest(e);
        }
    }

    @GetMapping("/{caseId}/contacts")
    public ResponseEntity<?> listContacts(@PathVariable Long caseId) {
        RequestContext ctx = RequestContextHolder.require();
        List<?> contacts = service.contacts(UUID.fromString(ctx.tenantId()), caseId);
        return ResponseEntity.ok(Map.of("items", contacts, "total", contacts.size()));
    }

    @PatchMapping("/contacts/{contactId}/trace-status")
    public ResponseEntity<?> updateTraceStatus(@PathVariable Long contactId,
                                               @RequestBody TraceStatusRequest body) {
        RequestContext ctx = RequestContextHolder.require();
        try {
            var saved = service.updateTraceStatus(UUID.fromString(ctx.tenantId()), contactId,
                    body.traceStatus());
            return ResponseEntity.ok(Map.of("data", saved));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    /**
     * The line list. Unpaged on purpose — it is an export of a cohort, and a page boundary in the
     * middle of an outbreak line list is how two people end up quoting different totals.
     */
    @GetMapping("/line-list")
    public ResponseEntity<?> lineList(@RequestParam(required = false) String caseType) {
        RequestContext ctx = RequestContextHolder.require();
        log.info("Generate line list [caseType={}] correlationId={}", caseType, ctx.correlationId());
        List<Map<String, Object>> rows = service.lineList(UUID.fromString(ctx.tenantId()), caseType);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", rows);
        body.put("total", rows.size());
        body.put("generated_at", java.time.OffsetDateTime.now().toString());
        return ResponseEntity.ok(body);
    }

    /**
     * A case update and a traced contact are audit-bearing: "who recorded this, and when" is the
     * first question a retrospective asks. RequestContext carries no actor, and attributing the
     * record to the pod (as the ingest path does) would put a service name where a person belongs.
     * So the write refuses rather than inventing an author.
     */
    private ResponseEntity<?> missingActor() {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "missing_actor",
                "message", "X-Actor-ID is required: an investigation record must name who made it."));
    }

    private ResponseEntity<?> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "invalid_case_investigation_request",
                "message", e.getMessage()));
    }
}
