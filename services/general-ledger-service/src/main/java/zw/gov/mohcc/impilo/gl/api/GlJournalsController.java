package zw.gov.mohcc.impilo.gl.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.gl.core.ChartOfAccountsService;
import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.gl.core.FiscalPeriodService;
import zw.gov.mohcc.impilo.gl.core.JournalService;
import zw.gov.mohcc.impilo.gl.persistence.entity.JournalEntryEntity;
import zw.gov.mohcc.impilo.gl.persistence.repository.JournalEntryRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * National general-ledger journals: draft, post, reverse.
 *
 * <h2>What was missing</h2>
 * <p>These are the estate's financial system of record. The three write endpoints had no
 * authorization check of any kind: any authenticated principal — including a signed-in citizen —
 * could create a journal, post it, and reverse it. Two things made expressing a guard impossible
 * before this change rather than merely absent:</p>
 *
 * <ul>
 *   <li>The controller read its context from the companion {@code RequestContextHolder}, which
 *       carries tenant, pod, request and correlation ids and <b>no actor type at all</b>. There was
 *       nothing to check. It now reads {@link TrustContextHolder}, which carries the actor identity
 *       the PDP stamps; the companion holder is still used for the v1.1 correlation surface it
 *       genuinely owns.</li>
 *   <li>{@code postedBy} and {@code reversedBy} were taken from the request body, defaulting to the
 *       literal string {@code "api"}. So the ledger's record of who posted an entry was whatever the
 *       poster typed — and in practice, for every caller that omitted the field, the word "api".
 *       They are now taken from the trust context and the body's values are ignored.</li>
 * </ul>
 *
 * <p>Reads are deliberately left as they are. They are not consequential writes and are outside this
 * change; that GL reads are available to any authenticated principal is recorded as a separate
 * finding rather than fixed silently here.</p>
 */
@RestController
@RequestMapping("/internal/v1/gl/journals")
public class GlJournalsController {

    /**
     * Posting to the national ledger is back-office work. See {@link ActorTypeGuard} for why this
     * cannot express "a finance officer specifically" — actor type is not a role, and no role
     * reaches a service today.
     */
    private static final java.util.Set<String> JOURNAL_WRITERS = ActorTypeGuard.BACK_OFFICE_WRITERS;

    private final JournalService journalService;
    private final JournalEntryRepository journalEntryRepository;
    private final ChartOfAccountsService chartOfAccountsService;
    private final FiscalPeriodService fiscalPeriodService;

    public GlJournalsController(JournalService journalService,
                                JournalEntryRepository journalEntryRepository,
                                ChartOfAccountsService chartOfAccountsService,
                                FiscalPeriodService fiscalPeriodService) {
        this.journalService = journalService;
        this.journalEntryRepository = journalEntryRepository;
        this.chartOfAccountsService = chartOfAccountsService;
        this.fiscalPeriodService = fiscalPeriodService;
    }

    @GetMapping
    public ResponseEntity<List<JournalEntryEntity>> byPeriod(@RequestParam("period_id") UUID periodId) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        return ResponseEntity.ok(journalService.findByPeriod(tenant, periodId));
    }

    @GetMapping("/{entryId}")
    public ResponseEntity<JournalEntryEntity> one(@PathVariable UUID entryId) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        return ResponseEntity.ok(journalEntryRepository.findById(entryId)
                .filter(e -> e.getTenantId().equals(tenant))
                .orElseThrow());
    }

    @PostMapping
    public ResponseEntity<JournalEntryEntity> create(@RequestBody JsonNode body) {
        TrustContext ctx = requireWriter("creating a general-ledger journal");
        UUID tenant = ctx.tenantId();
        LocalDate entryDate = LocalDate.parse(body.path("entryDate").asText());
        UUID periodId = body.hasNonNull("fiscalPeriodId")
                ? UUID.fromString(body.get("fiscalPeriodId").asText())
                : fiscalPeriodService.ensurePeriodForDate(tenant, entryDate).getPeriodId();
        List<JournalService.LineDraft> lines = new ArrayList<>();
        for (JsonNode ln : body.withArray("lines")) {
            UUID accountId;
            if (ln.hasNonNull("accountId")) {
                accountId = UUID.fromString(ln.get("accountId").asText());
            } else {
                accountId = chartOfAccountsService.findByCode(tenant, ln.get("accountCode").asText())
                        .orElseThrow()
                        .getAccountId();
            }
            lines.add(new JournalService.LineDraft(
                    accountId,
                    decimal(ln, "debit"),
                    decimal(ln, "credit"),
                    text(ln, "description"),
                    ln.hasNonNull("facilityId") ? UUID.fromString(ln.get("facilityId").asText()) : null
            ));
        }
        JournalEntryEntity je = journalService.createDraft(
                tenant,
                entryDate,
                periodId,
                text(body, "description"),
                body.path("sourceModule").asText("MANUAL"),
                text(body, "sourceRef"),
                lines
        );
        return ResponseEntity.ok(je);
    }

    /**
     * The body's {@code postedBy} is ignored. Who posted an entry is not something the poster gets
     * to assert.
     */
    @PostMapping("/{entryId}/post")
    public ResponseEntity<JournalEntryEntity> post(@PathVariable UUID entryId, @RequestBody(required = false) JsonNode body) {
        TrustContext ctx = requireWriter("posting a general-ledger journal");
        return ResponseEntity.ok(journalService.postEntry(ctx.tenantId(), entryId, ctx.actorId()));
    }

    /** The body's {@code reversedBy} is ignored, for the same reason as {@code postedBy}. */
    @PostMapping("/{entryId}/reverse")
    public ResponseEntity<JournalEntryEntity> reverse(@PathVariable UUID entryId, @RequestBody(required = false) JsonNode body) {
        TrustContext ctx = requireWriter("reversing a general-ledger journal");
        return ResponseEntity.ok(journalService.reverseEntry(ctx.tenantId(), entryId, ctx.actorId()));
    }

    /**
     * Authorize the caller and return the context the write will be attributed to.
     *
     * <p>Called before the journal entry is loaded, so an unauthorised caller cannot use the
     * endpoint to learn whether a given entry id exists.</p>
     */
    private static TrustContext requireWriter(String operation) {
        TrustContext ctx = TrustContextHolder.require();
        ActorTypeGuard.require(ctx, JOURNAL_WRITERS, operation);
        if (ctx.tenantId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    operation.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + operation.substring(1)
                            + " requires a resolvable " + TrustContext.H_TENANT_ID
                            + "; this request presents none, so there is no ledger to write to.");
        }
        if (ctx.actorId() == null || ctx.actorId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    operation.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + operation.substring(1)
                            + " requires " + TrustContext.H_ACTOR_ID
                            + "; the ledger records who posted every entry and this request presents nobody.");
        }
        return ctx;
    }

    private static BigDecimal decimal(JsonNode n, String field) {
        if (!n.has(field) || n.get(field).isNull()) {
            return null;
        }
        JsonNode v = n.get(field);
        return v.isNumber() ? v.decimalValue() : new BigDecimal(v.asText());
    }

    private static String text(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }
}
