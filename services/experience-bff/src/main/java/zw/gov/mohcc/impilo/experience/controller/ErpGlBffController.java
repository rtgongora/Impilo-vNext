package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.GeneralLedgerServiceClient;

import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/internal/v1/erp/gl")
public class ErpGlBffController {

    private final GeneralLedgerServiceClient client;

    public ErpGlBffController(GeneralLedgerServiceClient client) {
        this.client = client;
    }

    @GetMapping("/accounts")
    public ResponseEntity<Map<String, Object>> accounts(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return invoke(client::getAccounts, "GL accounts unavailable", requestId, correlationId);
    }

    @PostMapping("/accounts/seed")
    public ResponseEntity<Map<String, Object>> seed(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return invoke(client::postAccountsSeed, "GL account seed unavailable", requestId, correlationId);
    }

    @GetMapping("/periods/open")
    public ResponseEntity<Map<String, Object>> openPeriods(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return invoke(client::getOpenPeriods, "GL open periods unavailable", requestId, correlationId);
    }

    @GetMapping("/journals")
    public ResponseEntity<Map<String, Object>> journals(
            @RequestParam("period_id") String periodId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return invoke(() -> client.getJournals(periodId), "GL journals unavailable", requestId, correlationId);
    }

    @PostMapping("/journals")
    public ResponseEntity<Map<String, Object>> createJournal(
            @RequestBody JsonNode body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return invoke(() -> client.postJournal(body), "GL journal create unavailable", requestId, correlationId);
    }

    @PostMapping("/journals/{entryId}/post")
    public ResponseEntity<Map<String, Object>> postJournal(
            @PathVariable String entryId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return invoke(() -> client.postJournalPost(entryId), "GL journal post unavailable", requestId, correlationId);
    }

    @GetMapping("/trial-balance")
    public ResponseEntity<Map<String, Object>> trialBalance(
            @RequestParam("period_id") String periodId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return invoke(() -> client.getTrialBalance(periodId), "GL trial balance unavailable", requestId, correlationId);
    }

    @GetMapping("/financial-statements/income-statement")
    public ResponseEntity<Map<String, Object>> income(
            @RequestParam("period_id") String periodId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return invoke(() -> client.getIncomeStatement(periodId), "GL income statement unavailable", requestId, correlationId);
    }

    @GetMapping("/financial-statements/balance-sheet")
    public ResponseEntity<Map<String, Object>> balanceSheet(
            @RequestParam("period_id") String periodId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return invoke(() -> client.getBalanceSheet(periodId), "GL balance sheet unavailable", requestId, correlationId);
    }

    @GetMapping("/budgets")
    public ResponseEntity<Map<String, Object>> budgets(
            @RequestParam int fiscalYear,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return invoke(() -> client.getBudgets(fiscalYear), "GL budgets unavailable", requestId, correlationId);
    }

    private ResponseEntity<Map<String, Object>> invoke(Supplier<JsonNode> call,
                                                       String unavailableMessage,
                                                       String requestId,
                                                       String correlationId) {
        try {
            JsonNode data = call.get();
            return ResponseEntity.ok(Map.of(
                    "data", data,
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "GL_UNAVAILABLE", "message", unavailableMessage),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
