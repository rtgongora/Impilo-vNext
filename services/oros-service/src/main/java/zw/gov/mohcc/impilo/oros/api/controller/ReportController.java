package zw.gov.mohcc.impilo.oros.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.oros.api.dto.AmendRequest;
import zw.gov.mohcc.impilo.oros.api.dto.CriticalFlagRequest;
import zw.gov.mohcc.impilo.oros.api.dto.ExternalLinkRequest;
import zw.gov.mohcc.impilo.oros.api.dto.NoteRequest;
import zw.gov.mohcc.impilo.oros.api.dto.OrderSummaryDto;
import zw.gov.mohcc.impilo.oros.api.dto.ReportDto;
import zw.gov.mohcc.impilo.oros.api.dto.ReportRequest;
import zw.gov.mohcc.impilo.oros.core.ExternalResultLinkService;
import zw.gov.mohcc.impilo.oros.core.OrderQueryService;
import zw.gov.mohcc.impilo.oros.core.ReportService;
import zw.gov.mohcc.impilo.oros.integration.ShareSlipClient;
import zw.gov.mohcc.impilo.oros.persistence.entity.ResultEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for the diagnostic reporting lifecycle and requester results inbox.
 *
 * <p>Report authoring is order-scoped ({@code /v1/results/{orderId}/preliminary|final|amend|
 * addendum}); release, acknowledgement, and critical flagging are result-scoped
 * ({@code /v1/results/{resultId}/release|ack|critical/flag|critical/ack}). A finalized report is
 * never overwritten — amend/addendum create new versions (see {@link ReportService}).</p>
 */
@RestController
@RequestMapping("/v1/results")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportService reportService;
    private final ExternalResultLinkService externalResultLinkService;
    private final OrderQueryService orderQueryService;
    private final ObjectMapper objectMapper;

    public ReportController(ReportService reportService,
                            ExternalResultLinkService externalResultLinkService,
                            OrderQueryService orderQueryService,
                            ObjectMapper objectMapper) {
        this.reportService = reportService;
        this.externalResultLinkService = externalResultLinkService;
        this.orderQueryService = orderQueryService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{orderId}/preliminary")
    public ResponseEntity<ApiResponse<ReportDto>> preliminary(
            @PathVariable String orderId, @Valid @RequestBody ReportRequest request) {
        ResultEntity r = reportService.createPreliminary(orderId,
                json(request.summary()), request.impression(), request.recommendations(),
                json(request.docIds()), request.ziboResultCodes());
        return created(r);
    }

    @PostMapping("/{orderId}/final")
    public ResponseEntity<ApiResponse<ReportDto>> finalReport(
            @PathVariable String orderId, @Valid @RequestBody ReportRequest request) {
        ResultEntity r = reportService.createFinal(orderId,
                json(request.summary()), request.impression(), request.recommendations(),
                json(request.docIds()), request.ziboResultCodes());
        return created(r);
    }

    @PostMapping("/{orderId}/amend")
    public ResponseEntity<ApiResponse<ReportDto>> amend(
            @PathVariable String orderId, @Valid @RequestBody AmendRequest request) {
        ResultEntity r = reportService.amend(orderId, request.reason(),
                json(request.summary()), request.impression(), request.recommendations());
        return created(r);
    }

    @PostMapping("/{orderId}/addendum")
    public ResponseEntity<ApiResponse<ReportDto>> addendum(
            @PathVariable String orderId, @Valid @RequestBody AmendRequest request) {
        ResultEntity r = reportService.addendum(orderId, request.reason(),
                json(request.summary()), request.impression(), request.recommendations());
        return created(r);
    }

    @PostMapping("/{resultId}/release")
    public ResponseEntity<ApiResponse<ReportDto>> release(
            @PathVariable UUID resultId, @RequestBody(required = false) NoteRequest request) {
        ResultEntity r = reportService.release(resultId, note(request));
        return ok(r);
    }

    @PostMapping("/{resultId}/ack")
    public ResponseEntity<ApiResponse<ReportDto>> acknowledge(
            @PathVariable UUID resultId, @RequestBody(required = false) NoteRequest request) {
        ResultEntity r = reportService.acknowledge(resultId, note(request));
        return ok(r);
    }

    @PostMapping("/{resultId}/critical/flag")
    public ResponseEntity<ApiResponse<ReportDto>> flagCritical(
            @PathVariable UUID resultId, @Valid @RequestBody CriticalFlagRequest request) {
        ResultEntity r = reportService.flagCritical(resultId, request.reason());
        return ok(r);
    }

    @PostMapping("/{resultId}/critical/ack")
    public ResponseEntity<ApiResponse<ReportDto>> acknowledgeCritical(
            @PathVariable UUID resultId, @RequestBody(required = false) NoteRequest request) {
        ResultEntity r = reportService.acknowledge(resultId, note(request));
        return ok(r);
    }

    /** Issue a secure, OTP-protected, time-limited external link to a result (criterion E). */
    @PostMapping("/{resultId}/external-link")
    public ResponseEntity<ApiResponse<ShareSlipClient.ShareLinkRef>> externalLink(
            @PathVariable UUID resultId, @RequestBody(required = false) ExternalLinkRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        int expiryHours = request != null && request.expiryHours() != null ? request.expiryHours() : 72;
        int maxClaims = request != null && request.maxClaims() != null ? request.maxClaims() : 3;
        ShareSlipClient.ShareLinkRef ref = externalResultLinkService.issue(resultId, expiryHours, maxClaims);
        return ResponseEntity.ok(ApiResponse.ok(ref, correlationId));
    }

    /** Full report version chain for an order (newest first). */
    @GetMapping("/{orderId}/versions")
    public ResponseEntity<ApiResponse<List<ReportDto>>> versions(@PathVariable String orderId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<ReportDto> versions = reportService.versions(orderId).stream()
                .map(ReportDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(versions, correlationId));
    }

    /** Requester results inbox: orders with results ready for review. */
    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<List<OrderSummaryDto>>> inbox(
            @RequestParam(name = "requester", required = false) String requester) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        List<OrderSummaryDto> orders = orderQueryService.resultsInbox(requester).stream()
                .map(OrderSummaryDto::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(orders, correlationId));
    }

    // ── helpers ──

    private ResponseEntity<ApiResponse<ReportDto>> created(ResultEntity r) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(ReportDto.from(r), correlationId));
    }

    private ResponseEntity<ApiResponse<ReportDto>> ok(ResultEntity r) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(ReportDto.from(r), correlationId));
    }

    private static String note(NoteRequest request) {
        return request != null ? request.note() : null;
    }

    private String json(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize report field to JSON, using toString()", e);
            return value.toString();
        }
    }
}
