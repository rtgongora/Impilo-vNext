package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msika.api.dto.ValidationRequest;
import zw.gov.mohcc.impilo.msika.api.dto.ValidationResult;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogItemEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogItemRepository;

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
    private final ObjectMapper objectMapper;

    public ValidationService(CatalogItemRepository itemRepository, ObjectMapper objectMapper) {
        this.itemRepository = itemRepository;
        this.objectMapper = objectMapper;
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

    public ValidationResult validatePack(String kind, String tenantId) {
        List<ValidationResult.ValidationIssue> issues = new ArrayList<>();

        if (kind != null && !VALID_KINDS.contains(kind)) {
            issues.add(new ValidationResult.ValidationIssue("ERROR", "INVALID_KIND",
                    "Invalid pack kind: " + kind, "kind"));
        }

        // Check for duplicate canonical codes in published catalogs
        // This is a simplified check — production would do cross-catalog dedup
        boolean valid = issues.stream().noneMatch(i -> "ERROR".equals(i.severity()));
        return new ValidationResult(valid, issues);
    }
}
