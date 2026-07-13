package zw.gov.mohcc.impilo.wallet.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.wallet.persistence.entity.DepositIntentEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.DepositIntentRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bank→wallet cash-in via statement reference matching. A citizen sends a ZIPIT
 * / bank transfer to the platform's collection account quoting their deposit
 * reference code (IMP-XXXXXXXX). The bank statement (or bank push notification)
 * is fed here; each line is matched to a PENDING {@link DepositIntentEntity} by
 * that code and, when the amount agrees, confirmed — crediting the wallet.
 *
 * <p>Fail-closed and never-guessing: a line with no reference code, no matching
 * pending intent, or a mismatched amount is returned UNMATCHED for an operator
 * to resolve — money is never credited to a wallet the statement didn't name.</p>
 */
@Service
public class StatementMatchingService {

    private static final Logger log = LoggerFactory.getLogger(StatementMatchingService.class);

    /** The deposit reference code embedded anywhere in a statement narrative. */
    private static final Pattern REF_PATTERN = Pattern.compile("IMP-[A-Z2-9]{8}");

    private final DepositIntentRepository depositIntentRepository;
    private final FundingService fundingService;

    public StatementMatchingService(DepositIntentRepository depositIntentRepository,
                                    FundingService fundingService) {
        this.depositIntentRepository = depositIntentRepository;
        this.fundingService = fundingService;
    }

    /** One line of a bank statement / collection-account feed. */
    public record StatementLine(String narrative, BigDecimal amount, String bankRef) {}

    /** Per-line outcome. */
    public record MatchOutcome(String bankRef, String referenceCode, String result, String detail) {
        public static MatchOutcome matched(String bankRef, String code) {
            return new MatchOutcome(bankRef, code, "MATCHED", null);
        }
        public static MatchOutcome unmatched(String bankRef, String code, String detail) {
            return new MatchOutcome(bankRef, code, "UNMATCHED", detail);
        }
    }

    /**
     * Match a batch of statement lines against pending deposit intents. Confirmed
     * matches credit the wallet (idempotent per deposit); everything else is
     * reported UNMATCHED, never force-credited.
     */
    @Transactional
    public List<MatchOutcome> matchStatement(List<StatementLine> lines) {
        List<MatchOutcome> outcomes = new ArrayList<>(lines.size());
        for (StatementLine line : lines) {
            outcomes.add(matchLine(line));
        }
        long matched = outcomes.stream().filter(o -> "MATCHED".equals(o.result())).count();
        log.info("Statement matching: {}/{} lines matched", matched, lines.size());
        return outcomes;
    }

    private MatchOutcome matchLine(StatementLine line) {
        String code = extractReference(line.narrative());
        if (code == null) {
            return MatchOutcome.unmatched(line.bankRef(), null, "no Impilo reference in narrative");
        }
        DepositIntentEntity intent = depositIntentRepository.findByReferenceCode(code).orElse(null);
        if (intent == null) {
            return MatchOutcome.unmatched(line.bankRef(), code, "no deposit intent for reference");
        }
        if (!DepositIntentEntity.STATUS_PENDING.equals(intent.getStatus())) {
            // Already confirmed (idempotent re-import) or cancelled — safe, no double-credit.
            return MatchOutcome.unmatched(line.bankRef(), code,
                    "deposit not pending (status=" + intent.getStatus() + ")");
        }
        if (line.amount() == null || intent.getAmount().compareTo(line.amount()) != 0) {
            return MatchOutcome.unmatched(line.bankRef(), code,
                    "amount mismatch: expected " + intent.getAmount() + " got " + line.amount());
        }
        fundingService.confirmDepositByReference(code, line.bankRef(), "statement-import");
        return MatchOutcome.matched(line.bankRef(), code);
    }

    static String extractReference(String narrative) {
        if (narrative == null) {
            return null;
        }
        Matcher m = REF_PATTERN.matcher(narrative.toUpperCase(Locale.ROOT));
        return m.find() ? m.group() : null;
    }
}
