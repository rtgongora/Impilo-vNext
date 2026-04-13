package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.SupportServiceClient;

import java.util.*;

/**
 * Citizen support ticket and knowledge base endpoints. Delegates to support-service
 * via CommunityServiceClient.
 *
 * GET  /internal/v1/mobile/citizen/support/tickets
 * POST /internal/v1/mobile/citizen/support/tickets
 * GET  /internal/v1/mobile/citizen/support/articles
 */
@RestController
@RequestMapping("/internal/v1/mobile/citizen/support")
public class CitizenSupportController {

    private final SupportServiceClient supportClient;

    public CitizenSupportController(SupportServiceClient supportClient) {
        this.supportClient = supportClient;
    }

    public record CreateTicketBody(
            @NotBlank String category,
            @NotBlank String subject,
            @NotBlank String description,
            String priority
    ) {}

    @GetMapping("/tickets")
    public ResponseEntity<Map<String, Object>> listTickets(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        JsonNode tickets = supportClient.listTickets(status, null, null, null, page, size);

        // UUID patientId = resolvePatientId(tenantId, actorId);
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", tickets);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tickets")
    public ResponseEntity<Map<String, Object>> createTicket(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @Valid @RequestBody CreateTicketBody body) {

        Map<String, Object> ticketRequest = new LinkedHashMap<>();
        ticketRequest.put("reporterRef", actorId);
        ticketRequest.put("title", body.subject());
        ticketRequest.put("category", body.category());
        ticketRequest.put("description", body.description());
        ticketRequest.put("priority", body.priority() != null ? body.priority() : "MEDIUM");

        JsonNode result = supportClient.createTicket(ticketRequest);

        // jdbcTemplate.update("""
        //     INSERT INTO citizen_support_tickets (...) VALUES (...)
        //     """, ...);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", result);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/articles")
    public ResponseEntity<Map<String, Object>> listArticles(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        JsonNode articles = supportClient.listArticles(category, null, page, size);
        return ResponseEntity.ok(Map.of(
                "data", articles != null ? articles : List.of(),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)
        ));
    }
}
