package zw.gov.mohcc.impilo.khuluma.core;

import org.springframework.stereotype.Service;

/**
 * Recipient-aware safe disclosure (G-KH-05/06) — decides whether an outbound message/notification
 * about a subject may carry its full content to a given recipient, or must be reduced to a content-free
 * "safe summary" ("An update is available" rather than "Your HIV result is ready").
 *
 * <p>Security property: <b>fail-safe to redact</b>. Full content is released ONLY when the recipient is
 * the subject themselves, or a delegate (guardian/caregiver/CHW) with an active legal basis (consent) and
 * the content is not in a restricted category. Every other path — no relationship, no consent, restricted
 * category, or any uncertainty — yields the safe summary. The relationship is resolved upstream from
 * mvumo {@code DelegationService.resolve(...)}; this service is the pure decision over those facts so it
 * is exhaustively testable.
 */
@Service
public class SafeDisclosureService {

    /** Recipient's relationship to the subject (from mvumo delegation resolve). */
    public enum RecipientRelationship {
        SELF, GUARDIAN, CAREGIVER, CHW, FACILITY_STAFF, NONE
    }

    /** Sensitivity of the message content. RESTRICTED = legally protected categories (HIV, MH, SRH, etc.). */
    public enum MessageSensitivity {
        ROUTINE, SENSITIVE, RESTRICTED
    }

    /**
     * The disclosure decision and the body to actually send.
     *
     * @param fullContentAllowed whether the full body may be sent
     * @param body               the content to send (full body if allowed, else the safe summary)
     * @param redacted           true when the safe summary was substituted for the full body
     * @param reason             auditable reason code for the decision
     */
    public record Disclosure(boolean fullContentAllowed, String body, boolean redacted, String reason) {
    }

    private static final String DEFAULT_SAFE_SUMMARY =
            "An update is available. Please open the app to view it securely.";

    /**
     * Decide what content a recipient may receive.
     *
     * @param relationship   recipient's relationship to the subject
     * @param sensitivity    the message's sensitivity class
     * @param consentGranted whether an active consent/legal basis covers this recipient + purpose
     * @param fullBody       the full message body
     * @param safeSummary    the content-free summary to fall back to (null → a generic default)
     */
    public Disclosure decide(RecipientRelationship relationship, MessageSensitivity sensitivity,
                             boolean consentGranted, String fullBody, String safeSummary) {
        RecipientRelationship rel = relationship == null ? RecipientRelationship.NONE : relationship;
        MessageSensitivity sens = sensitivity == null ? MessageSensitivity.RESTRICTED : sensitivity; // unknown = most protected
        String summary = (safeSummary == null || safeSummary.isBlank()) ? DEFAULT_SAFE_SUMMARY : safeSummary;

        // The subject themselves always receives their own content.
        if (rel == RecipientRelationship.SELF) {
            return new Disclosure(true, fullBody, false, "SELF");
        }
        // No relationship at all → never any content.
        if (rel == RecipientRelationship.NONE) {
            return redact(summary, "NO_RELATIONSHIP");
        }
        // A delegate without an active legal basis → safe summary only.
        if (!consentGranted) {
            return redact(summary, "NO_CONSENT");
        }
        // Restricted categories are never pushed to a delegate — they must be viewed in-app under the
        // subject's own (or a category-specific) authorisation. General consent is not enough.
        if (sens == MessageSensitivity.RESTRICTED) {
            return redact(summary, "RESTRICTED_CATEGORY");
        }
        // Delegate + consent + non-restricted → full content within the delegation.
        return new Disclosure(true, fullBody, false, "DELEGATE_CONSENTED");
    }

    private static Disclosure redact(String summary, String reason) {
        return new Disclosure(false, summary, true, reason);
    }
}
