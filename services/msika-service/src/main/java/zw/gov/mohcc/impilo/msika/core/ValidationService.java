package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msika.api.dto.RiskFrictionView;
import zw.gov.mohcc.impilo.msika.api.dto.ValidationRequest;
import zw.gov.mohcc.impilo.msika.api.dto.ValidationResult;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogItemEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogItemRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.RiskFrictionMappingRepository;

import java.util.*;

@Service
public class ValidationService {

    private static final Set<String> VALID_KINDS = Set.of(
            "PRODUCT", "SERVICE", "ORDERABLE", "CHARGEABLE",
            "CAPABILITY_FACILITY", "CAPABILITY_PROVIDER"
    );

    private static final Set<String> RESTRICTION_KEYS = Set.of(
            "prescription_required", "facility_only", "provider_only_order",
            "controlled_item", "age_restricted", "cold_chain_required",
            "hazardous_handling_required", "requires_schedule",
            "requires_referral", "requires_licenced_provider"
    );

    private final CatalogItemRepository itemRepository;
    private final CatalogRepository catalogRepository;
    private final RiskFrictionMappingRepository riskFrictionRepository;
    private final ObjectMapper objectMapper;

    public ValidationService(CatalogItemRepository itemRepository,
                             CatalogRepository catalogRepository,
                             RiskFrictionMappingRepository riskFrictionRepository,
                             ObjectMapper objectMapper) {
        this.itemRepository = itemRepository;
        this.catalogRepository = catalogRepository;
        this.riskFrictionRepository = riskFrictionRepository;
        this.objectMapper = objectMapper;
    }

    /** Governed risk-class → friction mapping lookup; 404 (IllegalArgument) for unknown classes. */
    public RiskFrictionView riskFriction(String classification) {
        return riskFrictionRepository.findById(classification)
                .map(m -> new RiskFrictionView(m.getRiskClassification(), m.getFrictionLevel(),
                        m.isRequiresProvider(), m.isRequiresFacility(), m.getMaxQtyPerOrder(), m.getNotes()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown risk classification: " + classification));
    }

    public ValidationResult validateItem(ValidationRequest request) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        // Validate kind
        if (request.kind() != null && !VALID_KINDS.contains(request.kind())) {
            issues.add(new ValidationResult.ValidationIssue("ERROR", "INVALID_KIND",
                    "Invalid item kind: " + request.kind() + ". Valid: " + VALID_KINDS, "kind"));
        }

        // Validate canonical code format
        if (request.canonicalCode() != null) {
            if (request.canonicalCode().length() > 100) {
                issues.add(new ValidationResult.ValidationIssue("ERROR", "CODE_TOO_LONG",
                        "Canonical code exceeds 100 characters", "canonicalCode"));
            }
            if (!request.canonicalCode().matches("^[A-Za-z0-9_.\\-]+$")) {
                issues.add(new ValidationResult.ValidationIssue("WARNING", "CODE_FORMAT",
                        "Canonical code contains non-standard characters", "canonicalCode"));
            }
        }

        // Validate restrictions schema
        if (request.restrictions() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> restrictions = objectMapper.convertValue(request.restrictions(), Map.class);
                for (String key : restrictions.keySet()) {
                    if (!RESTRICTION_KEYS.contains(key)) {
                        issues.add(new ValidationResult.ValidationIssue("WARNING", "UNKNOWN_RESTRICTION",
                                "Unknown restriction key: " + key, "restrictions." + key));
                    }
                }
            } catch (Exception e) {
                issues.add(new ValidationResult.ValidationIssue("ERROR", "INVALID_RESTRICTIONS",
                        "Restrictions must be a valid JSON object", "restrictions"));
            }
        }

        // Validate ZIBO bindings
        if (request.ziboBindings() != null) {
            for (int i = 0; i < request.ziboBindings().length; i++) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> binding = objectMapper.convertValue(request.ziboBindings()[i], Map.class);
                    if (!binding.containsKey("system") || !binding.containsKey("code")) {
                        issues.add(new ValidationResult.ValidationIssue("ERROR", "MISSING_BINDING_FIELDS",
                                "ZIBO binding must have 'system' and 'code' fields", "ziboBindings[" + i + "]"));
                    }
                } catch (Exception e) {
                    issues.add(new ValidationResult.ValidationIssue("ERROR", "INVALID_BINDING",
                            "Invalid ZIBO binding at index " + i, "ziboBindings[" + i + "]"));
                }
            }
        }

        // Check if item exists (if itemId provided)
        if (request.itemId() != null) {
            Optional<CatalogItemEntity> item = itemRepository.findById(request.itemId());
            if (item.isEmpty()) {
                issues.add(new ValidationResult.ValidationIssue("ERROR", "ITEM_NOT_FOUND",
                        "Item not found: " + request.itemId(), "itemId"));
            }
        }

        boolean valid = issues.stream().noneMatch(i -> "ERROR".equals(i.severity()));
        return new ValidationResult(valid, issues);
    }

    /**
     * Real pack validation. Two modes:
     *
     * <p>Explicit item mode (itemIds provided): every id must exist, be active,
     * belong to a PUBLISHED catalog, and pass restriction-composition checks.
     *
     * <p>Whole-pack mode (no itemIds): validates every published item of the
     * given kind/tenant — duplicate canonical codes across published catalogs
     * and restriction-composition violations are surfaced.
     */
    public ValidationResult validatePack(String kind, String tenantId, List<String> itemIds) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (kind != null && !VALID_KINDS.contains(kind)) {
            issues.add(new ValidationResult.ValidationIssue("ERROR", "INVALID_KIND",
                    "Invalid pack kind: " + kind, "kind"));
            return new ValidationResult(false, issues);
        }

        if (itemIds != null && !itemIds.isEmpty()) {
            for (String itemId : itemIds) {
                Optional<CatalogItemEntity> item = itemRepository.findById(itemId);
                if (item.isEmpty()) {
                    issues.add(new ValidationResult.ValidationIssue("ERROR", "ITEM_NOT_FOUND",
                            "Item not found: " + itemId, "itemIds"));
                    continue;
                }
                validatePackItem(item.get(), kind, issues);
            }
        } else {
            List<CatalogItemEntity> items = itemRepository
                    .findPublishedItems(kind, null, null, null, tenantId, Pageable.unpaged())
                    .getContent();
            Map<String, List<String>> byCanonicalCode = new HashMap<>();
            for (CatalogItemEntity item : items) {
                byCanonicalCode.computeIfAbsent(item.getCanonicalCode(), k -> new ArrayList<>())
                        .add(item.getItemId());
                validatePackItem(item, kind, issues);
            }
            byCanonicalCode.forEach((code, ids) -> {
                if (ids.size() > 1) {
                    issues.add(new ValidationResult.ValidationIssue("ERROR", "DUPLICATE_CANONICAL_CODE",
                            "Canonical code " + code + " appears in " + ids.size()
                                    + " published items: " + String.join(", ", ids), "canonicalCode"));
                }
            });
        }

        boolean valid = issues.stream().noneMatch(i -> "ERROR".equals(i.severity()));
        return new ValidationResult(valid, issues);
    }

    /** Per-item pack checks: published-catalog membership, active flag, kind match, restriction composition. */
    private void validatePackItem(CatalogItemEntity item, String kind,
                                  List<ValidationResult.ValidationIssue> issues) {
        String field = "items." + item.getItemId();

        Optional<CatalogEntity> catalog = catalogRepository.findById(item.getCatalogId());
        if (catalog.isEmpty() || !"PUBLISHED".equals(catalog.get().getStatus())) {
            issues.add(new ValidationResult.ValidationIssue("ERROR", "NOT_IN_PUBLISHED_CATALOG",
                    "Item " + item.getItemId() + " belongs to catalog " + item.getCatalogId()
                            + " which is not PUBLISHED", field));
        }
        if (!item.isActive()) {
            issues.add(new ValidationResult.ValidationIssue("ERROR", "ITEM_INACTIVE",
                    "Item " + item.getItemId() + " is inactive", field));
        }
        if (kind != null && !kind.equals(item.getKind())) {
            issues.add(new ValidationResult.ValidationIssue("WARNING", "KIND_MISMATCH",
                    "Item " + item.getItemId() + " is kind " + item.getKind()
                            + ", pack kind is " + kind, field));
        }

        // Restriction composition: parse the stored restrictions and check coherence.
        Map<String, Object> restrictions;
        try {
            restrictions = objectMapper.readValue(
                    item.getRestrictions() != null ? item.getRestrictions() : "{}",
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception e) {
            issues.add(new ValidationResult.ValidationIssue("ERROR", "INVALID_RESTRICTIONS",
                    "Item " + item.getItemId() + " has unparseable restrictions JSON", field));
            return;
        }
        for (String key : restrictions.keySet()) {
            if (!RESTRICTION_KEYS.contains(key)) {
                issues.add(new ValidationResult.ValidationIssue("WARNING", "UNKNOWN_RESTRICTION",
                        "Item " + item.getItemId() + " carries unknown restriction key: " + key, field));
            }
        }
        // A controlled item without a prescription requirement is incoherent:
        // controlled custody always implies prescribing friction.
        if (restrictions.containsKey("controlled_item") && !restrictions.containsKey("prescription_required")) {
            issues.add(new ValidationResult.ValidationIssue("ERROR", "RESTRICTION_COMPOSE",
                    "Item " + item.getItemId()
                            + " is controlled_item but lacks prescription_required — incoherent composition", field));
        }
    }
}
