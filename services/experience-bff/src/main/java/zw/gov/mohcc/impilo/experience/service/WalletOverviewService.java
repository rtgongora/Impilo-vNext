package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.GuidanceServiceClient;
import zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoConsentServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified Person Health Wallet — overview composition.
 *
 * <p>The wallet is an <strong>experience / orchestration surface</strong>, not a system of record.
 * This service does not own any data: it composes the person anchor view from the existing
 * sovereign owners — VITO (profile), identity-assurance (trust), PCT/BUTANO (records &amp; care
 * journey via {@link CitizenHealthSummaryService}), tshepo-consent + mvumo (consent &amp; delegated
 * access), Costa (bills), notification-service (comms preferences), and guidance/Nompilo
 * (next-step prompts). It NEVER persists or duplicates clinical, identity, consent, or payment
 * truth.</p>
 *
 * <p>Each section is fetched defensively (try/catch) so a single downstream outage degrades that
 * one card to a truthful empty/unavailable state rather than failing the whole wallet. Every
 * section carries a {@code _source} label so the UI can show provenance, per Health OS doctrine
 * (no anonymous dashboard cards).</p>
 */
@Service
public class WalletOverviewService {

    private static final Logger log = LoggerFactory.getLogger(WalletOverviewService.class);

    /** Demographic fields that drive the profile-completion meter (no clinical fields here). */
    private static final List<String> PROFILE_FIELDS =
            List.of("givenName", "familyName", "dateOfBirth", "sex", "phone", "address");

    private final VitoServiceClient vitoClient;
    private final IdentityAssuranceServiceClient assuranceClient;
    private final CitizenHealthSummaryService healthSummaryService;
    private final PctServiceClient pctClient;
    private final TshepoConsentServiceClient consentClient;
    private final MvumoServiceClient mvumoClient;
    private final CostaServiceClient costaClient;
    private final NotificationServiceClient notificationClient;
    private final GuidanceServiceClient guidanceClient;

    public WalletOverviewService(VitoServiceClient vitoClient,
                                 IdentityAssuranceServiceClient assuranceClient,
                                 CitizenHealthSummaryService healthSummaryService,
                                 PctServiceClient pctClient,
                                 TshepoConsentServiceClient consentClient,
                                 MvumoServiceClient mvumoClient,
                                 CostaServiceClient costaClient,
                                 NotificationServiceClient notificationClient,
                                 GuidanceServiceClient guidanceClient) {
        this.vitoClient = vitoClient;
        this.assuranceClient = assuranceClient;
        this.healthSummaryService = healthSummaryService;
        this.pctClient = pctClient;
        this.consentClient = consentClient;
        this.mvumoClient = mvumoClient;
        this.costaClient = costaClient;
        this.notificationClient = notificationClient;
        this.guidanceClient = guidanceClient;
    }

    /**
     * Build the full wallet overview for the authenticated person ({@code actorId} = Health ID).
     * Content adapts to trust level: the trust card is always present; richer sections are only
     * surfaced when the underlying owner returns data the caller is permitted to see (the sovereign
     * services apply their own policy/visibility obligations — the wallet does not widen access).
     */
    public Map<String, Object> buildOverview(String actorId) {
        Map<String, Object> data = new LinkedHashMap<>();

        Map<String, Object> identity = identityAndTrust(actorId);
        data.put("identity", identity);
        data.put("profileCompletion", profileCompletion(identity.get("profile")));
        data.put("consent", consentSummary(actorId));
        data.put("dependants", dependantSummary(actorId));
        data.put("records", recordSummary(actorId));
        data.put("careTimeline", careTimeline(actorId));
        data.put("payments", paymentSummary());
        data.put("commsPreferences", commsPreferences(actorId));
        data.put("guidance", guidance(actorId));
        data.put("nextActions", nextActions(identity, data));

        return data;
    }

    // ── Identity & trust ────────────────────────────────────────────────────

    private Map<String, Object> identityAndTrust(String actorId) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("_source", "vito-service + identity-assurance-service");
        card.put("healthId", actorId);
        try {
            card.put("assurance", assuranceClient.getAssuranceStatus());
        } catch (Exception e) {
            log.debug("wallet: assurance status unavailable for actor: {}", e.getMessage());
            card.put("assurance", null);
            card.put("assuranceUnavailable", true);
        }
        try {
            card.put("profile", vitoClient.getCitizenProfile(actorId));
        } catch (Exception e) {
            log.debug("wallet: vito profile unavailable for actor: {}", e.getMessage());
            card.put("profile", null);
            card.put("profileUnavailable", true);
        }
        return card;
    }

    private Map<String, Object> profileCompletion(Object profileObj) {
        Map<String, Object> meter = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        int present = 0;
        if (profileObj instanceof JsonNode profile && profile.isObject()) {
            for (String field : PROFILE_FIELDS) {
                JsonNode v = profile.get(field);
                if (v != null && !v.isNull() && !(v.isTextual() && v.asText().isBlank())) {
                    present++;
                } else {
                    missing.add(field);
                }
            }
        } else {
            missing.addAll(PROFILE_FIELDS);
        }
        int total = PROFILE_FIELDS.size();
        meter.put("present", present);
        meter.put("total", total);
        meter.put("percent", total == 0 ? 0 : Math.round((present * 100.0f) / total));
        meter.put("missing", missing);
        return meter;
    }

    // ── Consent & delegated access ─────────────────────────────────────────

    private Map<String, Object> consentSummary(String actorId) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("_source", "tshepo-consent-service");
        try {
            JsonNode active = consentClient.listConsents(actorId, "ACTIVE", 0, 100);
            card.put("active", active);
            card.put("activeCount", countItems(active));
        } catch (Exception e) {
            log.debug("wallet: consent unavailable: {}", e.getMessage());
            card.put("active", null);
            card.put("activeCount", 0);
            card.put("unavailable", true);
        }
        return card;
    }

    private Map<String, Object> dependantSummary(String actorId) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("_source", "mvumo-service (delegations) + vito-service (relationships)");
        try {
            JsonNode actingFor = mvumoClient.listDelegationsForDelegate(actorId);
            card.put("iCanActFor", actingFor);
            card.put("iCanActForCount", countItems(actingFor));
        } catch (Exception e) {
            log.debug("wallet: delegate list unavailable: {}", e.getMessage());
            card.put("iCanActFor", null);
            card.put("iCanActForCount", 0);
        }
        try {
            JsonNode canActForMe = mvumoClient.listDelegationsForSubject("Patient/" + actorId);
            card.put("canActForMe", canActForMe);
            card.put("canActForMeCount", countItems(canActForMe));
        } catch (Exception e) {
            log.debug("wallet: subject delegation list unavailable: {}", e.getMessage());
            card.put("canActForMe", null);
            card.put("canActForMeCount", 0);
        }
        return card;
    }

    // ── Records & care journey ─────────────────────────────────────────────

    private Map<String, Object> recordSummary(String actorId) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("_source", "butano-service (IPS/SHR) + pct-service");
        try {
            // Reuse the existing, proven citizen summary composition — do not re-aggregate clinical
            // truth here. buildSummary already degrades gracefully per section.
            card.put("summary", healthSummaryService.buildSummary(actorId));
        } catch (Exception e) {
            log.debug("wallet: health summary unavailable: {}", e.getMessage());
            card.put("summary", Map.of());
            card.put("unavailable", true);
        }
        return card;
    }

    private Map<String, Object> careTimeline(String actorId) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("_source", "pct-service");
        try {
            JsonNode timeline = pctClient.getPatientTimeline(actorId);
            card.put("timeline", timeline);
            card.put("eventCount", countItems(timeline));
        } catch (Exception e) {
            log.debug("wallet: care timeline unavailable: {}", e.getMessage());
            card.put("timeline", null);
            card.put("eventCount", 0);
            card.put("unavailable", true);
        }
        return card;
    }

    // ── Payments ───────────────────────────────────────────────────────────

    private Map<String, Object> paymentSummary() {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("_source", "costa (billing) + mushex (payments)");
        try {
            // Outstanding = finalized bills awaiting settlement. Costa scopes to the trust
            // context. "FINAL" is the COSTA BillStatus at which the coverage split is settled
            // and the patient shortfall is known ("INVOICED" is not a valid BillStatus and
            // previously made this card permanently unavailable).
            JsonNode outstanding = costaClient.listBills(0, 20, "FINAL");
            card.put("outstanding", outstanding);
            card.put("outstandingCount", countItems(outstanding));
        } catch (Exception e) {
            log.debug("wallet: payments unavailable: {}", e.getMessage());
            card.put("outstanding", null);
            card.put("outstandingCount", 0);
            card.put("unavailable", true);
        }
        return card;
    }

    // ── Communication preferences ──────────────────────────────────────────

    private Map<String, Object> commsPreferences(String actorId) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("_source", "notification-service (Khuluma delivery)");
        try {
            card.put("preferences", notificationClient.getPreferences(actorId));
        } catch (Exception e) {
            log.debug("wallet: comms preferences unavailable: {}", e.getMessage());
            card.put("preferences", null);
            card.put("unavailable", true);
        }
        return card;
    }

    // ── Nompilo guidance ───────────────────────────────────────────────────

    private Map<String, Object> guidance(String actorId) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("_source", "guidance-service (Nompilo)");
        try {
            card.put("reminders", guidanceClient.getReminders());
        } catch (Exception e) {
            log.debug("wallet: guidance reminders unavailable: {}", e.getMessage());
            card.put("reminders", null);
        }
        return card;
    }

    // ── Derived "what next" prompts ────────────────────────────────────────

    /**
     * Compose graduated next-step prompts from the trust state and section counts. These are
     * surfaced as Nompilo-style guidance cards on the wallet home; they explain why things are
     * locked and the next best step rather than presenting dead buttons.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> nextActions(Map<String, Object> identity, Map<String, Object> data) {
        List<Map<String, Object>> actions = new ArrayList<>();

        Object assurance = identity.get("assurance");
        String state = assurance instanceof JsonNode a && a.hasNonNull("assuranceState")
                ? a.get("assuranceState").asText() : null;
        boolean fullyVerified = "FULLY_VERIFIED".equals(state);
        if (assurance == null || !fullyVerified) {
            actions.add(action("UPGRADE_TRUST",
                    "Verify your identity to unlock your full health wallet",
                    "/citizen/wallet/identity",
                    "Some records and actions require a higher trust level."));
        }

        Object completionObj = data.get("profileCompletion");
        if (completionObj instanceof Map<?, ?> completion) {
            Object percent = ((Map<String, Object>) completion).get("percent");
            if (percent instanceof Number n && n.intValue() < 100) {
                actions.add(action("COMPLETE_PROFILE",
                        "Complete your profile",
                        "/citizen/wallet/profile",
                        "A complete profile helps providers care for you safely."));
            }
        }

        Object payments = data.get("payments");
        if (payments instanceof Map<?, ?> p) {
            Object count = ((Map<String, Object>) p).get("outstandingCount");
            if (count instanceof Number n && n.intValue() > 0) {
                actions.add(action("SETTLE_BILL",
                        "You have outstanding bills",
                        "/citizen/wallet/payments",
                        "Review and settle bills, or arrange a payment plan."));
            }
        }
        return actions;
    }

    private Map<String, Object> action(String code, String title, String href, String why) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("code", code);
        a.put("title", title);
        a.put("href", href);
        a.put("why", why);
        return a;
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private int countItems(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }
        if (node.isArray()) {
            return node.size();
        }
        for (String key : List.of("content", "items", "data", "totalElements")) {
            JsonNode child = node.get(key);
            if (child != null) {
                if (child.isArray()) {
                    return child.size();
                }
                if (child.isNumber()) {
                    return child.asInt();
                }
            }
        }
        return 0;
    }
}
