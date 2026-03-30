package zw.gov.mohcc.impilo.zibo.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.zibo.api.dto.ArtifactSummaryDto;
import zw.gov.mohcc.impilo.zibo.api.dto.CreateValueSetRequest;
import zw.gov.mohcc.impilo.zibo.core.ZiboValidationService;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/value-sets")
public class ValueSetController {

    private final ZiboValidationService ziboValidationService;
    private final ObjectMapper objectMapper;

    public ValueSetController(ZiboValidationService ziboValidationService, ObjectMapper objectMapper) {
        this.ziboValidationService = ziboValidationService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ArtifactSummaryDto>> createValueSet(
            @Valid @RequestBody CreateValueSetRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        String contentStr = serializeContent(request.contentJson());

        ArtifactEntity artifact = ziboValidationService.createValueSet(
                request.canonicalUrl(),
                request.version(),
                request.name(),
                request.title(),
                request.description(),
                contentStr,
                request.publisher());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(ArtifactSummaryDto.from(artifact), correlationId));
    }

    @GetMapping("/snapshot")
    public ResponseEntity<Map<String, Object>> snapshot(
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "100") int limit) {

        OffsetDateTime asOf = OffsetDateTime.now();
        Page<ArtifactEntity> page = ziboValidationService.listValueSets(cursor, limit);

        List<Map<String, Object>> items = page.getContent().stream()
                .map(this::toSnapshotItem)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("as_of", asOf.toString());
        response.put("cursor", cursor);
        response.put("limit", limit);
        response.put("has_more", page.hasNext());
        response.put("total", page.getTotalElements());
        response.put("items", items);

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toSnapshotItem(ArtifactEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", entity.getArtifactId() != null ? entity.getArtifactId().toString() : null);
        item.put("type", "ValueSet");
        item.put("canonical_url", entity.getCanonicalUrl());
        item.put("version", entity.getVersion());
        item.put("name", entity.getName());
        item.put("status", entity.getStatus() != null ? entity.getStatus().name() : null);
        item.put("published_at", entity.getPublishedAt() != null ? entity.getPublishedAt().toString() : null);
        return item;
    }

    private String serializeContent(Object content) {
        if (content instanceof String s) return s;
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid content JSON", e);
        }
    }
}
