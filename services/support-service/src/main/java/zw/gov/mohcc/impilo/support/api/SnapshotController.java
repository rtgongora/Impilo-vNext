package zw.gov.mohcc.impilo.support.api;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.support.api.dto.*;
import zw.gov.mohcc.impilo.support.core.SupportService;
import zw.gov.mohcc.impilo.support.domain.*;

import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/internal/v1/snapshots")
public class SnapshotController {

    private final SupportService supportService;

    public SnapshotController(SupportService supportService) { this.supportService = supportService; }

    @GetMapping("/tickets")
    public ResponseEntity<?> snapshotTickets(@RequestParam(defaultValue = "0") int cursor,
                                              @RequestParam(defaultValue = "100") int limit,
                                              @RequestParam(name = "as_of", required = false) String asOfParam) {
        RequestContextHolder.require();
        OffsetDateTime asOf = asOfParam != null ? OffsetDateTime.parse(asOfParam) : OffsetDateTime.now();
        Page<TicketEntity> page = supportService.getTicketSnapshot(asOf, cursor, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toTicketResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit); body.put("has_more", page.hasNext());
        body.put("as_of", asOfParam != null ? asOfParam : asOf.toString());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/articles")
    public ResponseEntity<?> snapshotArticles(@RequestParam(defaultValue = "0") int cursor,
                                               @RequestParam(defaultValue = "100") int limit,
                                               @RequestParam(name = "as_of", required = false) String asOfParam) {
        RequestContextHolder.require();
        OffsetDateTime asOf = asOfParam != null ? OffsetDateTime.parse(asOfParam) : OffsetDateTime.now();
        Page<KnowledgeArticleEntity> page = supportService.getArticleSnapshot(asOf, cursor, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toArticleResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit); body.put("has_more", page.hasNext());
        body.put("as_of", asOfParam != null ? asOfParam : asOf.toString());
        return ResponseEntity.ok(body);
    }

    private TicketResponse toTicketResponse(TicketEntity t) {
        return new TicketResponse(t.getTicketId(), t.getTenantId(), t.getTitle(), t.getDescription(),
                t.getCategory(), t.getPriority(), t.getStatus(), t.getReporterRef(), t.getAssigneeRef(),
                t.getFacilityRef(), t.getResolution(), t.getRequestId(), t.getCorrelationId(),
                t.getEscalationLevel(), t.getEscalatedAt(), t.getEscalatedBy(), t.getSlaBreachedAt(),
                t.getVersion(), t.getCreatedAt(), t.getUpdatedAt(), t.getResolvedAt());
    }

    private ArticleResponse toArticleResponse(KnowledgeArticleEntity a) {
        return new ArticleResponse(a.getArticleId(), a.getTenantId(), a.getTitle(), a.getBody(),
                a.getCategory(), a.getStatus(), a.getAuthorRef(), a.getTags(), a.getVersion(),
                a.getCreatedAt(), a.getUpdatedAt(), a.getPublishedAt());
    }
}
