package zw.gov.mohcc.impilo.experience.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Computes the real <em>guidance signals</em> Nompilo evaluates against the guidance catalogue —
 * from the service state the BFF already aggregates. This keeps Nompilo free of fake intelligence:
 * a card only shows when a real signal fired.
 *
 * <p>For a citizen on a wallet route, the signals are derived from the existing
 * {@link WalletOverviewService} composition (trust state, profile completion, outstanding bills) —
 * the same state that drove the legacy hardcoded {@code nextActions}. For provider / facility
 * contexts, the BFF passes through the check-in / setup signals it already resolves from the trust
 * context, so the engine stays config-driven without the BFF re-deriving clinical state.</p>
 */
@Service
public class NompiloSignalService {

    private static final Logger log = LoggerFactory.getLogger(NompiloSignalService.class);

    private final WalletOverviewService walletService;

    public NompiloSignalService(WalletOverviewService walletService) {
        this.walletService = walletService;
    }

    /**
     * Derive the signals + trust LoA for a citizen from the wallet overview. Returns a holder with
     * the fired signal keys and the resolved trust LoA. Defensive: a downstream outage yields the
     * empty/low-trust signal set rather than failing — guidance degrades to a safe partial.
     */
    public CitizenSignals citizenSignals(String actorId, boolean firstTime) {
        Set<String> signals = new LinkedHashSet<>();
        int loa = 1;
        if (firstTime) {
            signals.add("first_time");
        }
        try {
            Map<String, Object> overview = walletService.buildOverview(actorId);

            // Trust state → not_verified signal + LoA.
            Object identity = overview.get("identity");
            if (identity instanceof Map<?, ?> id) {
                Object assurance = id.get("assurance");
                String state = assuranceState(assurance);
                if (!"FULLY_VERIFIED".equals(state)) {
                    signals.add("trust.not_verified");
                }
                loa = loaFor(state);
            } else {
                signals.add("trust.not_verified");
            }

            // Profile completion.
            Object completion = overview.get("profileCompletion");
            if (completion instanceof Map<?, ?> c) {
                Object percent = c.get("percent");
                if (percent instanceof Number n && n.intValue() < 100) {
                    signals.add("profile.incomplete");
                }
            }

            // Outstanding bills.
            Object payments = overview.get("payments");
            if (payments instanceof Map<?, ?> p) {
                Object count = p.get("outstandingCount");
                if (count instanceof Number n && n.intValue() > 0) {
                    signals.add("payments.outstanding");
                }
            }
        } catch (Exception e) {
            log.debug("Nompilo signals: wallet overview unavailable for {}: {}", actorId, e.getMessage());
            // Safe partial: assume under-trust so the verify card still guides the user.
            signals.add("trust.not_verified");
        }
        return new CitizenSignals(signals, loa);
    }

    private static String assuranceState(Object assurance) {
        if (assurance instanceof com.fasterxml.jackson.databind.JsonNode a && a.hasNonNull("assuranceState")) {
            return a.get("assuranceState").asText();
        }
        return null;
    }

    /** Map the assurance state to an approximate LoA so trust-gated guidance can be ranked. */
    private static int loaFor(String state) {
        if (state == null) return 1;
        return switch (state) {
            case "FULLY_VERIFIED" -> 4;
            case "VERIFIED", "DOCUMENT_VERIFIED" -> 3;
            case "PARTIALLY_VERIFIED", "EMAIL_VERIFIED", "PHONE_VERIFIED" -> 2;
            default -> 1;
        };
    }

    public record CitizenSignals(Set<String> signals, int trustLoa) {}
}
