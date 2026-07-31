package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.OrganizationRegistryServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Org-scoped regulatory desk reads that are not configuration authoring and not register
 * materialisation — restrictions list and configuration audit.
 */
@RestController
@RequestMapping("/api/v1/regulatory")
public class RegulatoryDeskController {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryDeskController.class);

    private final VarapiServiceClient varapiClient;
    private final OrganizationRegistryServiceClient orgRegistryClient;

    public RegulatoryDeskController(VarapiServiceClient varapiClient,
                                    OrganizationRegistryServiceClient orgRegistryClient) {
        this.varapiClient = varapiClient;
        this.orgRegistryClient = orgRegistryClient;
    }

    @GetMapping("/organizations/{organizationId}/restrictions")
    public ResponseEntity<Map<String, Object>> restrictions(
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String organizationId) {
        try {
            JsonNode catalogue = varapiClient.professionalRegistersForOrganisation(organizationId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("organizationId", organizationId);
            boolean linked = catalogue != null && catalogue.path("linked").asBoolean(false);
            body.put("linked", linked);
            Long councilId = catalogue == null || catalogue.path("councilId").isNull()
                    ? null : catalogue.path("councilId").asLong();
            body.put("councilId", councilId);
            body.put("councilCode", text(catalogue, "councilCode"));
            if (!linked || councilId == null) {
                body.put("restrictions", List.of());
                body.put("note", "No council is linked to this organisation, so there are no "
                        + "register-entry restrictions to list.");
                return ResponseEntity.ok(body);
            }
            JsonNode rows = varapiClient.registerEntryRestrictionsForCouncil(councilId);
            List<Map<String, Object>> restrictions = new ArrayList<>();
            if (rows != null && rows.isArray()) {
                rows.forEach(row -> restrictions.add(restrictionRow(row)));
            }
            body.put("restrictions", restrictions);
            body.put("note", "New restrictions are imposed through disciplinary determination.");
            return ResponseEntity.ok(body);
        } catch (HttpStatusCodeException ex) {
            log.warn("Restrictions read failed for organisation {} [{}]: {}",
                    organizationId, correlationId, ex.getStatusCode());
            return ResponseEntity.status(ex.getStatusCode()).build();
        } catch (Exception ex) {
            log.error("Restrictions read failed for organisation {} [{}]", organizationId, correlationId, ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping("/organizations/{organizationId}/audit-events")
    public ResponseEntity<Map<String, Object>> auditEvents(
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String organizationId) {
        try {
            JsonNode rows = orgRegistryClient.listConfigAuditEvents(organizationId);
            List<Map<String, Object>> events = new ArrayList<>();
            if (rows != null && rows.isArray()) {
                rows.forEach(row -> {
                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("id", text(row, "id"));
                    event.put("eventType", text(row, "eventType"));
                    event.put("subjectType", text(row, "subjectType"));
                    event.put("subjectId", text(row, "subjectId"));
                    event.put("actorHealthId", text(row, "actorHealthId"));
                    event.put("actorRole", text(row, "actorRole"));
                    event.put("occurredAt", text(row, "occurredAt"));
                    event.put("correlationId", text(row, "correlationId"));
                    events.add(event);
                });
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("organizationId", organizationId);
            body.put("events", events);
            body.put("scope", "CONFIGURATION");
            body.put("note", "This trail covers configuration pack and release acts for the "
                    + "organisation. It is not a full register-access desk.");
            return ResponseEntity.ok(body);
        } catch (HttpStatusCodeException ex) {
            log.warn("Audit read failed for organisation {} [{}]: {}",
                    organizationId, correlationId, ex.getStatusCode());
            return ResponseEntity.status(ex.getStatusCode()).build();
        } catch (Exception ex) {
            log.error("Audit read failed for organisation {} [{}]", organizationId, correlationId, ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private static Map<String, Object> restrictionRow(JsonNode row) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", row.path("id").isNull() ? null : row.path("id").asLong());
        body.put("restrictionType", text(row, "restrictionType"));
        body.put("description", text(row, "description"));
        body.put("status", text(row, "status"));
        body.put("validFrom", text(row, "validFrom"));
        body.put("validTo", text(row, "validTo"));
        body.put("imposedBy", text(row, "imposedBy"));
        body.put("decisionRef", text(row, "decisionRef"));
        body.put("registrationRecordId",
                row.path("registrationRecordId").isNull() ? null : row.path("registrationRecordId").asLong());
        body.put("registrationNumber", text(row, "registrationNumber"));
        body.put("providerId", row.path("providerId").isNull() ? null : row.path("providerId").asLong());
        body.put("registerCode", text(row, "registerCode"));
        body.put("registerName", text(row, "registerName"));
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
