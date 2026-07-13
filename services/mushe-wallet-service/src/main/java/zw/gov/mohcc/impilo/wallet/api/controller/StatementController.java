package zw.gov.mohcc.impilo.wallet.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.wallet.core.StatementMatchingService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bank collection-account statement ingest — the bank→wallet cash-in entry
 * point. A back-office feed (or the bank's push webhook) posts statement lines;
 * matched lines confirm the depositor's pending intent and credit the wallet.
 */
@RestController
@RequestMapping("/internal/v1/statements")
public class StatementController {

    private final StatementMatchingService statementMatchingService;

    public StatementController(StatementMatchingService statementMatchingService) {
        this.statementMatchingService = statementMatchingService;
    }

    /**
     * Ingest a batch of statement lines. Body: {@code {"lines":[{"narrative","amount","bankRef"}]}}.
     * Returns per-line MATCHED/UNMATCHED outcomes for the ops reconciliation view.
     */
    @PostMapping("/match")
    public ResponseEntity<ApiResponse<List<StatementMatchingService.MatchOutcome>>> match(
            @RequestHeader(value = CompanionHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestBody Map<String, Object> body) {

        List<StatementMatchingService.StatementLine> lines = new ArrayList<>();
        Object rawLines = body.get("lines");
        if (rawLines instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    String narrative = str(m.get("narrative"));
                    BigDecimal amount = decimal(m.get("amount"));
                    String bankRef = str(m.get("bankRef"));
                    lines.add(new StatementMatchingService.StatementLine(narrative, amount, bankRef));
                }
            }
        }
        List<StatementMatchingService.MatchOutcome> outcomes =
                statementMatchingService.matchStatement(lines);
        return ResponseEntity.ok(ApiResponse.ok(outcomes, correlationId));
    }

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private static BigDecimal decimal(Object o) {
        if (o == null) return null;
        try {
            return new BigDecimal(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
