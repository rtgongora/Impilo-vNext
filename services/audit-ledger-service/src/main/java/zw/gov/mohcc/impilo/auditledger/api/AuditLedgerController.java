package zw.gov.mohcc.impilo.auditledger.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.auditledger.api.dto.*;
import zw.gov.mohcc.impilo.auditledger.core.AuditLedgerService;
import zw.gov.mohcc.impilo.auditledger.domain.AuditRecordEntity;
import zw.gov.mohcc.impilo.shared.auth.ActorTypeGuard;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.*;

@RestController
public class AuditLedgerController {

    private static final Logger log = LoggerFactory.getLogger(AuditLedgerController.class);
    private final AuditLedgerService auditService;
    private final ObjectMapper objectMapper;

    public AuditLedgerController(AuditLedgerService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /**
     * Append a record to the ledger.
     *
     * <p>{@code 73cc29d27} gave this service authentication — it previously had no security
     * dependency and no filter chain at all — but authentication only: the chain ends in
     * {@code anyRequest().authenticated()}, so every authenticated principal in the estate,
     * including every signed-in citizen, could write the national audit ledger. Authenticating a
     * caller establishes who they are and says nothing about whether they may do this.</p>
     *
     * <p>Appending to the ledger is a machine operation: services record what they did. So the gate
     * is {@link ActorTypeGuard#MACHINE_ONLY} — no human session of any kind. That is stricter than
     * the back-office ceiling used for the general ledger and the waiver desk, and it is affordable
     * here because the endpoint currently has no caller: measured across services/ and ui/, nothing
     * issues this request, and only a base-URL property holder in experience-bff even names the
     * service. Being strict costs nothing today and is far cheaper now than after the first
     * integration.</p>
     *
     * <p>Who the record is <em>about</em> — {@code actorId} / {@code actorType} on the body — stays
     * body-supplied, deliberately. A service recording "citizen 42 read record X" must be able to
     * name citizen 42; overriding that with the calling service's own identity would not harden the
     * ledger, it would destroy what the ledger says. That is the opposite of the tshepo-audit ingest
     * fix, where the BFF forwards the acting user's own trust context and the body's actor was
     * therefore redundant as well as forgeable. Both surfaces are audit writes; they are not the
     * same seam, and treating them identically would have been wrong at one of the two.</p>
     */
    @PostMapping("/internal/v1/audit/records")
    public ResponseEntity<?> appendRecord(@Valid @RequestBody AppendAuditRecordRequest request,
                                           jakarta.servlet.http.HttpServletRequest httpRequest) {
        // Before anything is read or written.
        ActorTypeGuard.require(TrustContextHolder.get(), ActorTypeGuard.MACHINE_ONLY,
                "appending a record to the audit ledger");
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        AuditRecordEntity record = auditService.appendRecord(UUID.fromString(ctx.tenantId()),
                ctx.podId(), ctx.correlationId(), idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(record));
    }

    @GetMapping("/internal/v1/audit/records/{record_id}")
    public ResponseEntity<?> getRecord(@PathVariable("record_id") UUID recordId) {
        RequestContext ctx = RequestContextHolder.require();
        return auditService.getRecord(recordId)
                .map(r -> ResponseEntity.ok((Object) toResponse(r)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorEnvelope.of("NOT_FOUND", "Audit record not found", ctx.requestId(), ctx.correlationId())));
    }

    @GetMapping("/internal/v1/audit/records")
    public ResponseEntity<?> listRecords(@RequestParam(defaultValue = "0") int cursor,
                                          @RequestParam(defaultValue = "50") int limit) {
        RequestContext ctx = RequestContextHolder.require();
        Page<AuditRecordEntity> page = auditService.listRecords(UUID.fromString(ctx.tenantId()), cursor, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("total_elements", page.getTotalElements());
        body.put("has_more", page.hasNext());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/internal/v1/audit/query")
    public ResponseEntity<?> queryByCorrelation(@RequestParam("correlation_id") UUID correlationId) {
        RequestContextHolder.require();
        List<AuditRecordEntity> records = auditService.queryByCorrelationId(correlationId);
        return ResponseEntity.ok(Map.of("items", records.stream().map(this::toResponse).toList(),
                "count", records.size()));
    }

    @GetMapping("/internal/v1/audit/chain/verify")
    public ResponseEntity<?> verifyChain(@RequestParam(name = "from_seq", defaultValue = "1") long fromSeq,
                                          @RequestParam(name = "to_seq", defaultValue = "1000") long toSeq) {
        RequestContext ctx = RequestContextHolder.require();
        boolean valid = auditService.verifyChainIntegrity(UUID.fromString(ctx.tenantId()), fromSeq, toSeq);
        return ResponseEntity.ok(Map.of("valid", valid, "from_seq", fromSeq, "to_seq", toSeq,
                "tenant_id", ctx.tenantId()));
    }

    private AuditRecordResponse toResponse(AuditRecordEntity r) {
        Object detail = parseJsonSafe(r.getDetailJson());
        return new AuditRecordResponse(r.getRecordId(), r.getTenantId(), r.getSequenceNum(),
                r.getCorrelationId(), r.getActorId(), r.getActorType(), r.getAction(),
                r.getResourceType(), r.getResourceId(), r.getOutcome(), detail,
                r.getOccurredAt(), r.getPrevHash(), r.getEntryHash());
    }

    private Object parseJsonSafe(String json) {
        if (json == null) return null;
        try { return objectMapper.readValue(json, Object.class); }
        catch (Exception e) { return json; }
    }
}
