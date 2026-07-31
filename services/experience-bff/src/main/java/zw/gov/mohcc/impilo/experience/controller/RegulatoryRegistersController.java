package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operator view of a council's materialised professional registers (NCZ task #97).
 *
 * <p>Composed deliberately rather than proxied raw — same doctrine as
 * {@link RegulatoryConfigurationController}. The shell must see provenance
 * ({@code CONFIG_PACK} vs {@code MIGRATION_SEED}, retired, config version) so an operator can tell
 * the fix from the leak repeated. Reconcile is a write of register rows only; it is not
 * configuration authoring and does not sit on the read-only configuration page.</p>
 */
@RestController
@RequestMapping("/api/v1/regulatory")
public class RegulatoryRegistersController {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryRegistersController.class);

    private final VarapiServiceClient varapiClient;

    public RegulatoryRegistersController(VarapiServiceClient varapiClient) {
        this.varapiClient = varapiClient;
    }

    @GetMapping("/organizations/{organizationId}/registers")
    public ResponseEntity<Map<String, Object>> registers(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String organizationId) {
        try {
            JsonNode raw = varapiClient.professionalRegistersForOrganisation(organizationId);
            return ResponseEntity.ok(composeCatalogue(organizationId, raw));
        } catch (HttpStatusCodeException ex) {
            log.warn("Register catalogue read failed for organisation {} [{}]: {}",
                    organizationId, correlationId, ex.getStatusCode());
            return ResponseEntity.status(ex.getStatusCode()).build();
        } catch (Exception ex) {
            log.error("Register catalogue read failed for organisation {} [{}]",
                    organizationId, correlationId, ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @PostMapping("/organizations/{organizationId}/registers/reconcile")
    public ResponseEntity<Map<String, Object>> reconcile(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String organizationId) {
        try {
            JsonNode raw = varapiClient.reconcileProfessionalRegisters(organizationId);
            return ResponseEntity.ok(composeReport(organizationId, raw));
        } catch (HttpStatusCodeException ex) {
            log.warn("Register reconcile failed for organisation {} [{}]: {}",
                    organizationId, correlationId, ex.getStatusCode());
            return ResponseEntity.status(ex.getStatusCode()).build();
        } catch (Exception ex) {
            log.error("Register reconcile failed for organisation {} [{}]",
                    organizationId, correlationId, ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private static Map<String, Object> composeCatalogue(String organizationId, JsonNode raw) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organizationId", organizationId);
        body.put("linked", raw != null && raw.path("linked").asBoolean(false));
        body.put("councilId", raw == null || raw.path("councilId").isNull()
                ? null : raw.path("councilId").asLong());
        body.put("councilCode", text(raw, "councilCode"));
        body.put("councilName", text(raw, "councilName"));
        body.put("note", text(raw, "note"));

        List<Map<String, Object>> registers = new ArrayList<>();
        JsonNode rows = raw == null ? null : raw.path("registers");
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                registers.add(composeRegister(row));
            }
        }
        body.put("registers", registers);

        long fromConfig = registers.stream()
                .filter(r -> "CONFIG_PACK".equals(r.get("source")))
                .count();
        long fromMigration = registers.stream()
                .filter(r -> "MIGRATION_SEED".equals(r.get("source")))
                .count();
        long retired = registers.stream()
                .filter(r -> "RETIRED".equals(r.get("status")))
                .count();
        body.put("summary", Map.of(
                "total", registers.size(),
                "fromConfiguration", fromConfig,
                "fromMigrationSeed", fromMigration,
                "retired", retired));
        return body;
    }

    private static Map<String, Object> composeRegister(JsonNode row) {
        Map<String, Object> register = new LinkedHashMap<>();
        register.put("id", row.path("id").isMissingNode() || row.path("id").isNull()
                ? null : row.path("id").asLong());
        register.put("registerCode", text(row, "registerCode"));
        register.put("name", text(row, "name"));
        register.put("description", text(row, "description"));
        register.put("status", text(row, "status"));
        register.put("registerAxis", text(row, "registerAxis"));
        register.put("statutoryTitle", text(row, "statutoryTitle"));
        register.put("statutoryTitleDecisionRef", text(row, "statutoryTitleDecisionRef"));
        register.put("source", text(row, "source"));
        register.put("configDefinitionKey", text(row, "configDefinitionKey"));
        register.put("configSemanticVersion", text(row, "configSemanticVersion"));
        register.put("configContentHash", text(row, "configContentHash"));
        register.put("materialisedAt", text(row, "materialisedAt"));
        register.put("retiredAt", text(row, "retiredAt"));
        return register;
    }

    private static Map<String, Object> composeReport(String organizationId, JsonNode raw) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organizationId", organizationId);
        body.put("linked", raw != null && raw.path("linked").asBoolean(false));
        body.put("outcome", text(raw, "outcome"));
        body.put("councilCode", text(raw, "councilCode"));
        body.put("declared", raw == null ? 0 : raw.path("declared").asInt(0));
        body.put("created", raw == null ? 0 : raw.path("created").asInt(0));
        body.put("updated", raw == null ? 0 : raw.path("updated").asInt(0));
        body.put("unchanged", raw == null ? 0 : raw.path("unchanged").asInt(0));
        body.put("retired", raw == null ? 0 : raw.path("retired").asInt(0));
        body.put("reinstated", raw == null ? 0 : raw.path("reinstated").asInt(0));
        body.put("note", text(raw, "note"));

        List<String> notes = new ArrayList<>();
        JsonNode notesNode = raw == null ? null : raw.path("notes");
        if (notesNode != null && notesNode.isArray()) {
            notesNode.forEach(n -> {
                if (n != null && !n.isNull() && !n.asText().isBlank()) {
                    notes.add(n.asText());
                }
            });
        }
        body.put("notes", notes);
        return body;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return value == null || value.isBlank() ? null : value;
    }
}
