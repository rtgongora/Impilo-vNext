package zw.gov.mohcc.impilo.msika.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msika.api.dto.CatalogItemView;
import zw.gov.mohcc.impilo.msika.api.dto.CreateItemRequest;
import zw.gov.mohcc.impilo.msika.api.dto.ServiceDetailDto;
import zw.gov.mohcc.impilo.msika.core.ItemService;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogItemEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogItemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/internal/v1/service-catalogs")
public class ServiceCatalogController {

    private final ItemService itemService;
    private final CatalogItemRepository catalogItemRepository;

    public ServiceCatalogController(ItemService itemService, CatalogItemRepository catalogItemRepository) {
        this.itemService = itemService;
        this.catalogItemRepository = catalogItemRepository;
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CatalogItemView>> createServiceItem(
            @Valid @RequestBody CreateServiceItemRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        CreateItemRequest itemRequest = new CreateItemRequest(
                "SERVICE",
                request.canonicalCode(),
                request.displayName(),
                request.description(),
                request.synonyms(),
                request.tags(),
                null,
                null,
                null,
                null,
                request.service(),
                null,
                null
        );
        CatalogItemView view = itemService.createItem(request.catalogId(), itemRequest);
        return ResponseEntity.status(201).body(ApiResponse.ok(view, correlationId));
    }

    @GetMapping("/snapshot")
    public ResponseEntity<Map<String, Object>> serviceSnapshot(
            @RequestParam(defaultValue = "0") int cursor,
            @RequestParam(defaultValue = "100") int limit) {

        OffsetDateTime asOf = OffsetDateTime.now();
        Page<CatalogItemEntity> page = catalogItemRepository.findByKind(
                "SERVICE", PageRequest.of(cursor, Math.min(limit, 500), Sort.by("itemId")));

        List<Map<String, Object>> items = page.getContent().stream()
                .map(this::toSnapshotItem)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("as_of", asOf.toString());
        response.put("cursor", cursor);
        response.put("limit", limit);
        response.put("has_more", page.hasNext());
        response.put("items", items);

        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toSnapshotItem(CatalogItemEntity entity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", entity.getItemId());
        item.put("kind", entity.getKind());
        item.put("catalog_id", entity.getCatalogId());
        item.put("canonical_code", entity.getCanonicalCode());
        item.put("display_name", entity.getDisplayName());
        item.put("active", entity.isActive());
        item.put("updated_at", entity.getUpdatedAt() != null ? entity.getUpdatedAt().toString() : null);
        return item;
    }

    public record CreateServiceItemRequest(
        @NotBlank String catalogId,
        @NotBlank String canonicalCode,
        @NotBlank String displayName,
        String description,
        String[] synonyms,
        String[] tags,
        @NotNull ServiceDetailDto service
    ) {}
}
