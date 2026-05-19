package zw.gov.mohcc.impilo.mushex.service.rail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.mushex.config.MushexProperties;
import zw.gov.mohcc.impilo.mushex.domain.entity.PaymentIntentEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.domain.enums.RailEnforcementReason;
import zw.gov.mohcc.impilo.mushex.service.adapter.AdapterRegistry;

/**
 * Pure helper that resolves the rail an attempt should use, based on the intent's
 * persisted rail-selection metadata. Never mutates the intent. Throws a
 * {@link RailEnforcementException} when the resolved adapter is unregistered at
 * attempt time.
 *
 * <p>See {@code docs/design/phase-3-attempt-time-rail-enforcement-implementation.md} §4.
 *
 * <p>The helper is split out of {@link zw.gov.mohcc.impilo.mushex.service.PaymentAttemptService}
 * so the decision can be unit-tested in isolation against a mocked {@link AdapterRegistry}
 * and an inline JSON metadata string.
 */
@Component
public class RailEnforcement {

    private static final Logger log = LoggerFactory.getLogger(RailEnforcement.class);

    private final ObjectMapper objectMapper;

    public RailEnforcement(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Resolve the effective rail for the intent.
     *
     * @param intent   the persisted intent; its {@code metadata} string is parsed defensively.
     * @param config   the rail-selection config (provides {@code defaultRail} for legacy intents).
     * @param registry the live adapter registry — used to verify the resolved rail is callable.
     * @return the rail-enforcement outcome (never {@code null}).
     * @throws RailEnforcementException when the resolved rail's adapter is not registered or
     *                                  when the metadata names an unknown rail.
     */
    public RailEnforcementOutcome resolve(PaymentIntentEntity intent,
                                          MushexProperties.RailSelection config,
                                          AdapterRegistry registry) {
        String metadataJson = intent.getMetadata();
        JsonNode metadata = parseMetadata(metadataJson);

        if (isImpiloSimulation(metadata)) {
            AdapterType sandbox = AdapterType.SANDBOX;
            if (!registry.has(sandbox)) {
                throw new RailEnforcementException(
                        RailEnforcementException.CODE_RAIL_UNAVAILABLE,
                        "SANDBOX adapter is not registered; impilo_simulation cannot be honoured");
            }
            AdapterType preferred = readPreferredRail(metadata);
            return new RailEnforcementOutcome(sandbox, preferred, RailEnforcementReason.SIMULATION_LOCK,
                    false, readSelectionVersion(metadata));
        }

        JsonNode railSelection = metadata != null ? metadata.get("rail_selection") : null;

        if (railSelection == null || railSelection.isNull() || !railSelection.isObject()) {
            return resolveLegacy(intent, config, registry);
        }

        JsonNode effectiveNode = railSelection.get("effective_rail");
        if (effectiveNode == null || effectiveNode.isNull() || effectiveNode.asText().isBlank()) {
            return resolveLegacy(intent, config, registry);
        }

        AdapterType effective;
        try {
            effective = AdapterType.valueOf(effectiveNode.asText().trim());
        } catch (IllegalArgumentException ex) {
            log.warn("Unknown effective_rail {} on intent {}; rejecting attempt",
                    effectiveNode.asText(), intent.getIntentId());
            throw new RailEnforcementException(
                    RailEnforcementException.CODE_RAIL_UNAVAILABLE,
                    "Intent's effective_rail '" + effectiveNode.asText()
                            + "' is not a known AdapterType");
        }

        if (!registry.has(effective)) {
            throw new RailEnforcementException(
                    RailEnforcementException.CODE_RAIL_UNAVAILABLE,
                    "Intent's effective_rail " + effective
                            + " is not registered with any adapter at attempt time");
        }

        AdapterType preferred = readPreferredRail(metadata);
        return new RailEnforcementOutcome(effective, preferred,
                RailEnforcementReason.EXPLICIT_METADATA, false,
                readSelectionVersion(metadata));
    }

    private RailEnforcementOutcome resolveLegacy(PaymentIntentEntity intent,
                                                 MushexProperties.RailSelection config,
                                                 AdapterRegistry registry) {
        AdapterType fallback = config.getDefaultRail();
        if (fallback == null) {
            fallback = AdapterType.SANDBOX;
        }
        if (!registry.has(fallback)) {
            throw new RailEnforcementException(
                    RailEnforcementException.CODE_RAIL_UNAVAILABLE,
                    "Default rail " + fallback + " from mushex.rail-selection.default-rail is not registered");
        }
        log.info("Intent {} has no rail_selection metadata; falling back to {}",
                intent.getIntentId(), fallback);
        return new RailEnforcementOutcome(fallback, null,
                RailEnforcementReason.LEGACY_FALLBACK, true, null);
    }

    private JsonNode parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode n = objectMapper.readTree(metadataJson);
            return n != null && n.isObject() ? n : null;
        } catch (Exception e) {
            log.debug("Could not parse intent metadata as JSON object: {}", e.getMessage());
            return null;
        }
    }

    private boolean isImpiloSimulation(JsonNode metadata) {
        if (metadata == null) {
            return false;
        }
        return (metadata.hasNonNull("impilo_simulation") && metadata.get("impilo_simulation").asBoolean())
                || (metadata.hasNonNull("impiloSimulation") && metadata.get("impiloSimulation").asBoolean());
    }

    private AdapterType readPreferredRail(JsonNode metadata) {
        if (metadata == null) {
            return null;
        }
        JsonNode rs = metadata.get("rail_selection");
        if (rs == null || rs.isNull() || !rs.isObject()) {
            return null;
        }
        JsonNode pref = rs.get("preferred_rail");
        if (pref == null || pref.isNull() || pref.asText().isBlank()) {
            return null;
        }
        try {
            return AdapterType.valueOf(pref.asText().trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String readSelectionVersion(JsonNode metadata) {
        if (metadata == null) {
            return null;
        }
        JsonNode rs = metadata.get("rail_selection");
        if (rs == null || rs.isNull() || !rs.isObject()) {
            return null;
        }
        JsonNode v = rs.get("selection_version");
        return v == null || v.isNull() ? null : v.asText();
    }
}
