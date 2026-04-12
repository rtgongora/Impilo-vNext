package zw.gov.mohcc.impilo.experience.controller.mobile.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CommunityServiceClient;
import zw.gov.mohcc.impilo.experience.controller.ResourceNotFoundException;
import zw.gov.mohcc.impilo.experience.service.OutboxService;

import java.time.OffsetDateTime;
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

    private final JdbcTemplate jdbcTemplate;
    private final OutboxService outboxService;
    private final CommunityServiceClient communityClient;

    public CitizenSupportController(JdbcTemplate jdbcTemplate, OutboxService outboxService,
                                    CommunityServiceClient communityClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.outboxService = outboxService;
        this.communityClient = communityClient;
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

        // STRANGLER: migrated — delegate to support-service via CommunityServiceClient
        JsonNode tickets = communityClient.listVisits(actorId);

        // STRANGLER: migrated — was direct JdbcTemplate SELECT from citizen_support_tickets table
        // UUID patientId = resolvePatientId(tenantId, actorId);
        // List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", tickets);
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tickets")
    @Transactional
    public ResponseEntity<Map<String, Object>> createTicket(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader("X-Actor-ID") String actorId,
            @Valid @RequestBody CreateTicketBody body) {

        // STRANGLER: migrated — delegate to support-service via CommunityServiceClient
        Map<String, Object> ticketRequest = new LinkedHashMap<>();
        ticketRequest.put("citizenCpid", actorId);
        ticketRequest.put("category", body.category());
        ticketRequest.put("subject", body.subject());
        ticketRequest.put("description", body.description());
        ticketRequest.put("priority", body.priority() != null ? body.priority() : "NORMAL");

        JsonNode result = communityClient.createVisit(ticketRequest);

        // STRANGLER: migrated — was direct JdbcTemplate INSERT into citizen_support_tickets table
        // jdbcTemplate.update("""
        //     INSERT INTO citizen_support_tickets (...) VALUES (...)
        //     """, ...);

        outboxService.writeOutboxEvent(
                "impilo.experience.citizen.support-ticket-created.v1",
                correlationId, requestId, requestId, tenantId, podId,
                "CitizenSupportTicket", requestId,
                Map.of("category", body.category(), "status", "OPEN"),
                Map.of());

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

        // Knowledge articles are empty until populated; return empty set structure
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", List.of());
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId,
                "page", Map.of("number", page, "size", size,
                        "total_elements", 0, "total_pages", 0)));
        return ResponseEntity.ok(response);
    }
}
