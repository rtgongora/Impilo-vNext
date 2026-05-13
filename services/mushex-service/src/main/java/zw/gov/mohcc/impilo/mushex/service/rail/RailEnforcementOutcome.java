package zw.gov.mohcc.impilo.mushex.service.rail;

import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.domain.enums.RailEnforcementReason;

/**
 * Pure-record result of {@link RailEnforcement#resolve}. Captures what rail the
 * attempt-creation flow will use, why it was chosen, and whether the intent it was
 * resolved against carried any rail-selection metadata at all.
 *
 * <p>The {@code selectionVersion} pass-through allows future readers to spot intents
 * that were created before the rail-selection schema was introduced.
 *
 * @param effectiveRail     the rail to actually use for {@link
 *                          zw.gov.mohcc.impilo.mushex.service.adapter.PaymentRailAdapter#initiatePayment}
 * @param preferredRail     the rail the intent caller originally preferred, if any
 *                          (informational; may be {@code null})
 * @param reason            why this rail was chosen
 * @param legacyIntent      {@code true} only when {@code reason == LEGACY_FALLBACK}
 * @param selectionVersion  value of {@code metadata.rail_selection.selection_version}
 *                          when present; {@code null} for legacy intents
 */
public record RailEnforcementOutcome(
        AdapterType effectiveRail,
        AdapterType preferredRail,
        RailEnforcementReason reason,
        boolean legacyIntent,
        String selectionVersion
) {}
