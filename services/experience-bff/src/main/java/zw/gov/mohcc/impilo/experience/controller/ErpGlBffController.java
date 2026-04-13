package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.GeneralLedgerServiceClient;

@RestController
@RequestMapping("/internal/v1/erp/gl")
public class ErpGlBffController {

    private final GeneralLedgerServiceClient client;

    public ErpGlBffController(GeneralLedgerServiceClient client) {
        this.client = client;
    }

    @GetMapping("/accounts")
    public ResponseEntity<JsonNode> accounts() {
        return ResponseEntity.ok(client.getAccounts());
    }

    @PostMapping("/accounts/seed")
    public ResponseEntity<JsonNode> seed() {
        return ResponseEntity.ok(client.postAccountsSeed());
    }

    @GetMapping("/periods/open")
    public ResponseEntity<JsonNode> openPeriods() {
        return ResponseEntity.ok(client.getOpenPeriods());
    }

    @GetMapping("/journals")
    public ResponseEntity<JsonNode> journals(@RequestParam("period_id") String periodId) {
        return ResponseEntity.ok(client.getJournals(periodId));
    }

    @PostMapping("/journals")
    public ResponseEntity<JsonNode> createJournal(@RequestBody JsonNode body) {
        return ResponseEntity.ok(client.postJournal(body));
    }

    @PostMapping("/journals/{entryId}/post")
    public ResponseEntity<JsonNode> postJournal(@PathVariable String entryId) {
        return ResponseEntity.ok(client.postJournalPost(entryId));
    }

    @GetMapping("/trial-balance")
    public ResponseEntity<JsonNode> trialBalance(@RequestParam("period_id") String periodId) {
        return ResponseEntity.ok(client.getTrialBalance(periodId));
    }

    @GetMapping("/financial-statements/income-statement")
    public ResponseEntity<JsonNode> income(@RequestParam("period_id") String periodId) {
        return ResponseEntity.ok(client.getIncomeStatement(periodId));
    }

    @GetMapping("/financial-statements/balance-sheet")
    public ResponseEntity<JsonNode> balanceSheet(@RequestParam("period_id") String periodId) {
        return ResponseEntity.ok(client.getBalanceSheet(periodId));
    }

    @GetMapping("/budgets")
    public ResponseEntity<JsonNode> budgets(@RequestParam int fiscalYear) {
        return ResponseEntity.ok(client.getBudgets(fiscalYear));
    }
}
