package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

/**
 * Decides, honestly, whether a facility can be the place a woman gives birth — matching the level of
 * care she needs against what the facility is actually known to provide.
 *
 * <p>This is a composition, not a source of truth: it reads tuso's facility status-summary and EmONC
 * readiness and combines them. The clinical determination of what level she NEEDS is made upstream
 * (the antenatal birth-care-level classification); this service answers whether a given facility
 * meets it.
 *
 * <p><b>Three honesty rules, each a way facility routing lies:</b>
 * <ul>
 *   <li><b>{@code operational} is the only gate, and its absence fails safe.</b> A facility is a
 *       confirmed destination only when tuso says {@code operational=true} — which is stricter than
 *       {@code status=ACTIVE}, because 5,509 of the estate's facilities are a regulator listing
 *       awaiting configuration. If the field is missing or the summary cannot be read, the answer is
 *       "not confirmed", never a default of confirmed. That is the opposite polarity to a disclosure
 *       badge: an unknown regulatory status is not a place to send a labouring woman.</li>
 *   <li><b>"We don't know" is never "no".</b> EmONC {@code INSUFFICIENT_EVIDENCE} — nobody has
 *       assessed this facility — is reported as unknown capability with "call ahead", and must never
 *       be collapsed into {@code NEITHER} ("assessed, and it cannot"). The difference is whether she
 *       phones ahead or drives past, and almost every maternity facility on the estate is unassessed,
 *       so this is the common case, not the edge one.</li>
 *   <li><b>A read failure fails loud.</b> If tuso cannot be reached, the verdict is UNAVAILABLE — not
 *       "cannot give birth here" and not "can". An error rendered as a capability verdict is a
 *       fabricated one, in either direction.</li>
 * </ul>
 */
@Service
public class BirthDestinationService {

    private static final Logger log = LoggerFactory.getLogger(BirthDestinationService.class);

    private final TusoServiceClient tusoClient;

    public BirthDestinationService(TusoServiceClient tusoClient) {
        this.tusoClient = tusoClient;
    }

    /** The level of care the woman needs, as determined upstream. */
    public enum RequiredLevel { CEMONC, BEMONC, UNKNOWN }

    public enum DestinationStatus {
        /** Operational, and its EmONC readiness meets the level she needs. */
        MEETS_REQUIRED_LEVEL,
        /** Operational, but assessed as below the level she needs — choose another or refer. */
        BELOW_REQUIRED_LEVEL,
        /** Operational, but its emergency obstetric capability has never been assessed. Call ahead. */
        CAPABILITY_UNKNOWN,
        /** Fails the operational gate (or its operational status is unknown) — not a confirmed destination. */
        NOT_OPERATIONAL,
        /** Her required level of care has not been established, so no facility can be matched to it. */
        REQUIRED_LEVEL_UNKNOWN,
        /** Tuso could not be reached. Neither safe nor unsafe — undetermined. */
        UNAVAILABLE
    }

    public record Verdict(
            long facilityId,
            RequiredLevel requiredLevel,
            DestinationStatus status,
            boolean operationalGatePassed,
            String emoncVerdict,
            boolean callAhead,
            String message,
            String guidance) {
    }

    public Verdict assess(long facilityId, RequiredLevel requiredLevel) {
        RequiredLevel need = requiredLevel == null ? RequiredLevel.UNKNOWN : requiredLevel;

        JsonNode statusSummary;
        JsonNode emonc;
        try {
            statusSummary = tusoClient.getFacilityStatusSummary(facilityId);
            emonc = tusoClient.getFacilityEmoncReadiness(facilityId);
        } catch (Exception e) {
            // Fail loud. A labouring woman's destination is not a place to render a network error as
            // a clinical verdict — in either direction.
            log.error("Birth-destination assessment could not reach tuso for facility {}: {}",
                    facilityId, e.getMessage());
            return new Verdict(facilityId, need, DestinationStatus.UNAVAILABLE, false, null, true,
                    "The facility's status could not be retrieved.",
                    "Do not treat this as either a safe or an unsafe destination — it is unknown. "
                            + "Confirm by phone before directing her here.");
        }

        boolean operational = readOperational(statusSummary);
        if (!operational) {
            // Covers both "assessed as not operational" and "operational status unknown/unreadable"
            // — the fail-safe collapses them, because for a birth destination the safe default of an
            // unknown is NOT confirmed.
            return new Verdict(facilityId, need, DestinationStatus.NOT_OPERATIONAL, false,
                    emoncVerdict(emonc), true,
                    "This facility is not a confirmed operating maternity destination.",
                    "Its operational status is not confirmed. Direct her to a facility whose operating "
                            + "status is confirmed, and call ahead.");
        }

        if (need == RequiredLevel.UNKNOWN) {
            return new Verdict(facilityId, need, DestinationStatus.REQUIRED_LEVEL_UNKNOWN, true,
                    emoncVerdict(emonc), true,
                    "The level of care she needs for birth has not been established.",
                    "Complete the birth-care-level assessment first, then match her to a facility.");
        }

        String verdict = emoncVerdict(emonc);
        // "We don't know" — never collapsed into "no".
        if (verdict == null || verdict.equals("INSUFFICIENT_EVIDENCE") || verdict.equals("UNKNOWN")) {
            return new Verdict(facilityId, need, DestinationStatus.CAPABILITY_UNKNOWN, true,
                    verdict, true,
                    "Nothing is known about this facility's emergency obstetric capability.",
                    "This is not a statement that it has none — it has not been assessed. Call ahead "
                            + "to confirm it can provide " + levelWords(need) + " before directing her here.");
        }

        boolean meets = switch (need) {
            case CEMONC -> verdict.equals("CEMONC");
            case BEMONC -> verdict.equals("CEMONC") || verdict.equals("BEMONC");
            case UNKNOWN -> false;
        };
        if (meets) {
            return new Verdict(facilityId, need, DestinationStatus.MEETS_REQUIRED_LEVEL, true,
                    verdict, true,
                    "This facility meets the level of care she needs (" + levelWords(need) + ").",
                    "Confirm by phone that it is ready to receive her.");
        }
        return new Verdict(facilityId, need, DestinationStatus.BELOW_REQUIRED_LEVEL, true,
                verdict, true,
                "This facility is assessed below the level she needs (" + levelWords(need) + ").",
                "Direct her to a facility that provides " + levelWords(need) + ", or arrange referral. "
                        + "Call ahead.");
    }

    /**
     * The operational gate, fail-safe. Reads tuso's {@code operational} boolean and treats anything
     * that is not an explicit {@code true} — false, missing, null, a non-boolean, an unreadable
     * summary — as NOT operational. For a birth destination, the safe default of an unknown is "not
     * confirmed"; this is deliberately the opposite of a disclosure badge, which defaults an unknown
     * to shown.
     */
    private static boolean readOperational(JsonNode statusSummary) {
        if (statusSummary == null) {
            return false;
        }
        JsonNode operational = statusSummary.get("operational");
        return operational != null && operational.isBoolean() && operational.booleanValue();
    }

    private static String emoncVerdict(JsonNode emonc) {
        if (emonc == null) {
            return null;
        }
        // tuso's EmoncReadinessService serialises the verdict under "classification" (the
        // Classification enum → a string). "verdict" is accepted as an alias so a later rename of
        // the tuso field does not silently drop the reading to null.
        JsonNode v = emonc.get("classification");
        if (v == null || !v.isTextual()) {
            v = emonc.get("verdict");
        }
        return v != null && v.isTextual() ? v.asText() : null;
    }

    private static String levelWords(RequiredLevel level) {
        return switch (level) {
            case CEMONC -> "comprehensive emergency obstetric care (including caesarean and transfusion)";
            case BEMONC -> "basic emergency obstetric care";
            case UNKNOWN -> "the required care";
        };
    }
}
