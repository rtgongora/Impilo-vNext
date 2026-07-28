package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.OrganizationRegistryServiceClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The read-only view of a regulator's own configuration (NCZ-W1A).
 *
 * <p>Read-only is the whole design of this wave, not a limitation of it: the Registry's authoring
 * path is a governed act with four-eyes behind it, and a screen that could bypass that would undo
 * the guarantee. There is no visual configuration studio here and none is planned for Wave 1.</p>
 *
 * <p>The responses are composed deliberately rather than proxied raw. A raw passthrough of the
 * org-registry entities would make the shell's typed hooks agree with whatever the entity happens
 * to serialise today, and that class of coupling has already broken typed hooks in this codebase.
 * The keys below are the contract.</p>
 *
 * <p>The two things this surface must show honestly, because they are the things a council needs to
 * act on: a value that is <em>built but not set</em>, with the decision it is waiting on; and a
 * source pack that <em>failed to load</em>, together with the fact that the activated configuration
 * is unaffected by that failure.</p>
 */
@RestController
@RequestMapping("/api/v1/regulatory")
public class RegulatoryConfigurationController {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryConfigurationController.class);

    private final OrganizationRegistryServiceClient orgRegistryClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public RegulatoryConfigurationController(OrganizationRegistryServiceClient orgRegistryClient,
                                             com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.orgRegistryClient = orgRegistryClient;
        this.objectMapper = objectMapper;
    }

    /** Every configuration pack a regulator holds, with the definitions currently governing it. */
    @GetMapping("/organizations/{organizationId}/configuration")
    public ResponseEntity<Map<String, Object>> configuration(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String organizationId) {
        try {
            JsonNode packs = orgRegistryClient.listConfigPacks(organizationId);
            List<Map<String, Object>> composed = new ArrayList<>();
            if (packs != null && packs.isArray()) {
                for (JsonNode pack : packs) {
                    composed.add(composePack(pack));
                }
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("organizationId", organizationId);
            body.put("packs", composed);
            return ResponseEntity.ok(body);
        } catch (HttpStatusCodeException ex) {
            log.warn("Configuration read failed for organisation {} [{}]: {}",
                    organizationId, correlationId, ex.getStatusCode());
            return ResponseEntity.status(ex.getStatusCode()).build();
        } catch (Exception ex) {
            log.error("Configuration read failed for organisation {} [{}]", organizationId, correlationId, ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    /** Every version of one definition, newest first — what changed, when, and under whose hand. */
    @GetMapping("/configuration/definitions/{definitionId}/versions")
    public ResponseEntity<Map<String, Object>> versionHistory(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String definitionId) {
        try {
            JsonNode versions = orgRegistryClient.configDefinitionVersions(definitionId);
            List<Map<String, Object>> composed = new ArrayList<>();
            if (versions != null && versions.isArray()) {
                for (JsonNode version : versions) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", text(version, "id"));
                    row.put("semanticVersion", text(version, "semanticVersion"));
                    row.put("lifecycleState", text(version, "lifecycleState"));
                    row.put("authoredBy", text(version, "authoredBy"));
                    row.put("approvedAt", text(version, "approvedAt"));
                    row.put("effectiveFrom", text(version, "effectiveFrom"));
                    row.put("contentHash", text(version, "contentHash"));
                    row.put("policyStatus", text(version, "policyStatus"));
                    row.put("policyDecisionRef", text(version, "policyDecisionRef"));
                    row.put("sourcePackRef", text(version, "sourcePackRef"));
                    composed.add(row);
                }
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("definitionId", definitionId);
            body.put("versions", composed);
            return ResponseEntity.ok(body);
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).build();
        } catch (Exception ex) {
            log.error("Version history read failed for definition {} [{}]", definitionId, correlationId, ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private Map<String, Object> composePack(JsonNode pack) {
        String packId = text(pack, "id");
        Map<String, Object> composed = new LinkedHashMap<>();
        composed.put("packId", packId);
        composed.put("packKey", text(pack, "packKey"));
        composed.put("label", text(pack, "label"));
        composed.put("description", text(pack, "description"));

        // The source pack's own honesty about itself. A LOAD_FAILED pack matters to an operator,
        // and it matters just as much that the activated configuration is NOT affected by it —
        // otherwise the screen reads as an outage when it is a file that needs attention.
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("loadState", pack.path("loadState").asText("NEVER_LOADED"));
        source.put("loadFailureReason", text(pack, "loadFailureReason"));
        source.put("loadCheckedAt", text(pack, "loadCheckedAt"));
        source.put("sourceRef", text(pack, "sourceRef"));
        source.put("activatedConfigurationAffected", false);
        composed.put("source", source);

        composed.put("releases", releases(packId));

        List<com.fasterxml.jackson.databind.node.ObjectNode> definitions = new ArrayList<>();
        boolean activated = true;
        try {
            JsonNode active = orgRegistryClient.activeConfiguration(packId);
            if (active != null && active.isArray()) {
                for (JsonNode definition : active) {
                    definitions.add(composeDefinition(definition));
                }
            }
        } catch (HttpStatusCodeException ex) {
            // A pack whose release has not been activated yet is the ordinary state of a freshly
            // seeded pack, not an error. Say so rather than showing an empty screen.
            if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw ex;
            }
            activated = false;
        }
        composed.put("activated", activated);
        composed.put("definitions", definitions);

        // Definition ids are needed for version history; the active projection does not carry them.
        composed.put("definitionIndex", definitionIndex(packId));
        return composed;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode composeDefinition(JsonNode definition) {
        com.fasterxml.jackson.databind.node.ObjectNode row = objectMapper.createObjectNode();
        row.put("typeCode", text(definition, "typeCode"));
        row.put("definitionKey", text(definition, "definitionKey"));
        row.put("label", text(definition, "label"));
        row.put("semanticVersion", text(definition, "semanticVersion"));
        row.put("selfServiceRoute", text(definition, "selfServiceRoute"));
        row.put("contentHash", text(definition, "contentHash"));
        row.put("pinnedAtMoment", text(definition, "pinnedAtMoment"));
        // The payload IS the configuration. Withholding it would leave the council able to see
        // that a rule exists but not what it says, and would leave role-aware navigation with no
        // source for the capability lists it must honour.
        row.set("payload", definition.path("payload").isMissingNode()
                ? null : definition.get("payload"));

        // A value the council has not set must read as awaiting a named decision — never as blank,
        // and never as zero. This is the field the whole screen exists to render honestly.
        String policyStatus = definition.path("policyStatus").asText(null);
        com.fasterxml.jackson.databind.node.ObjectNode policy = objectMapper.createObjectNode();
        policy.put("status", policyStatus);
        policy.put("decisionRef", text(definition, "policyDecisionRef"));
        policy.put("valueSet", policyStatus == null || "CONFIRMED".equals(policyStatus));
        row.set("policy", policy);
        return row;
    }

    private List<Map<String, Object>> releases(String packId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            JsonNode releases = orgRegistryClient.listConfigReleases(packId);
            if (releases != null && releases.isArray()) {
                for (JsonNode release : releases) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", text(release, "id"));
                    row.put("releaseKey", text(release, "releaseKey"));
                    row.put("label", text(release, "label"));
                    row.put("lifecycleState", text(release, "lifecycleState"));
                    row.put("submittedBy", text(release, "submittedBy"));
                    row.put("activatedAt", text(release, "activatedAt"));
                    rows.add(row);
                }
            }
        } catch (Exception ex) {
            log.warn("Could not read releases for configuration pack {}", packId, ex);
        }
        return rows;
    }

    private Map<String, String> definitionIndex(String packId) {
        Map<String, String> index = new LinkedHashMap<>();
        try {
            JsonNode definitions = orgRegistryClient.listConfigDefinitions(packId);
            if (definitions != null && definitions.isArray()) {
                for (JsonNode definition : definitions) {
                    index.put(definition.path("typeCode").asText("") + ":"
                            + definition.path("definitionKey").asText(""), text(definition, "id"));
                }
            }
        } catch (Exception ex) {
            log.warn("Could not index definitions for configuration pack {}", packId, ex);
        }
        return index;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
