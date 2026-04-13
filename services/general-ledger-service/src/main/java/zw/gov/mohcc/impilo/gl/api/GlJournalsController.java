package zw.gov.mohcc.impilo.gl.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.gl.core.ChartOfAccountsService;
import zw.gov.mohcc.impilo.gl.core.FiscalPeriodService;
import zw.gov.mohcc.impilo.gl.core.JournalService;
import zw.gov.mohcc.impilo.gl.persistence.entity.JournalEntryEntity;
import zw.gov.mohcc.impilo.gl.persistence.repository.JournalEntryRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/gl/journals")
public class GlJournalsController {

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
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
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

    @PostMapping("/{entryId}/post")
    public ResponseEntity<JournalEntryEntity> post(@PathVariable UUID entryId, @RequestBody(required = false) JsonNode body) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        String by = body != null && body.hasNonNull("postedBy") ? body.get("postedBy").asText() : "api";
        return ResponseEntity.ok(journalService.postEntry(tenant, entryId, by));
    }

    @PostMapping("/{entryId}/reverse")
    public ResponseEntity<JournalEntryEntity> reverse(@PathVariable UUID entryId, @RequestBody(required = false) JsonNode body) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        String by = body != null && body.hasNonNull("reversedBy") ? body.get("reversedBy").asText() : "api";
        return ResponseEntity.ok(journalService.reverseEntry(tenant, entryId, by));
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
