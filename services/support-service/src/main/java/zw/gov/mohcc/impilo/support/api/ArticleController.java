package zw.gov.mohcc.impilo.support.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.support.api.dto.*;
import zw.gov.mohcc.impilo.support.core.SupportService;
import zw.gov.mohcc.impilo.support.domain.KnowledgeArticleEntity;

import java.util.*;

@RestController
public class ArticleController {

    private final SupportService supportService;

    public ArticleController(SupportService supportService) { this.supportService = supportService; }

    @PostMapping("/internal/v1/support/articles")
    public ResponseEntity<?> createArticle(@Valid @RequestBody CreateArticleRequest request,
                                            jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        KnowledgeArticleEntity article = supportService.createArticle(UUID.fromString(ctx.tenantId()),
                ctx.podId(), ctx.correlationId(), idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(article));
    }

    @PatchMapping("/internal/v1/support/articles/{article_id}")
    public ResponseEntity<?> updateArticle(@PathVariable("article_id") UUID articleId,
                                            @RequestBody UpdateArticleRequest request,
                                            jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        try {
            KnowledgeArticleEntity article = supportService.updateArticle(articleId,
                    UUID.fromString(ctx.tenantId()), ctx.podId(), ctx.correlationId(), idempotencyKey, request);
            return ResponseEntity.ok(toResponse(article));
        } catch (SupportService.NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @GetMapping("/internal/v1/support/articles/{article_id}")
    public ResponseEntity<?> getArticle(@PathVariable("article_id") UUID articleId) {
        RequestContext ctx = RequestContextHolder.require();
        return supportService.getArticle(articleId)
                .map(a -> ResponseEntity.ok((Object) toResponse(a)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ErrorEnvelope.of("NOT_FOUND", "Article not found", ctx.requestId(), ctx.correlationId())));
    }

    @GetMapping("/internal/v1/support/articles")
    public ResponseEntity<?> listArticles(@RequestParam(required = false) String category,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "0") int cursor,
                                           @RequestParam(defaultValue = "20") int limit) {
        RequestContext ctx = RequestContextHolder.require();
        Page<KnowledgeArticleEntity> page = supportService.listArticles(UUID.fromString(ctx.tenantId()),
                category, status, cursor, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("total_elements", page.getTotalElements());
        body.put("has_more", page.hasNext());
        return ResponseEntity.ok(body);
    }

    private ArticleResponse toResponse(KnowledgeArticleEntity a) {
        return new ArticleResponse(a.getArticleId(), a.getTenantId(), a.getTitle(), a.getBody(),
                a.getCategory(), a.getStatus(), a.getAuthorRef(), a.getTags(), a.getVersion(),
                a.getCreatedAt(), a.getUpdatedAt(), a.getPublishedAt());
    }
}
