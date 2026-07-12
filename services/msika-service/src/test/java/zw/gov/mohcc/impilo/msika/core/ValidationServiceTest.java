package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.msika.api.dto.RiskFrictionView;
import zw.gov.mohcc.impilo.msika.api.dto.ValidationRequest;
import zw.gov.mohcc.impilo.msika.api.dto.ValidationResult;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogItemEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.RiskFrictionMappingEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogItemRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.RiskFrictionMappingRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

    @Mock private CatalogItemRepository itemRepository;
    @Mock private CatalogRepository catalogRepository;
    @Mock private RiskFrictionMappingRepository riskFrictionRepository;
    private ValidationService validationService;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        validationService = new ValidationService(itemRepository, catalogRepository,
                riskFrictionRepository, objectMapper);
    }

    @Test
    void validateItem_validKind() {
        ValidationRequest request = new ValidationRequest(null, "PRODUCT", "MED-001", null, null);
        ValidationResult result = validationService.validateItem(request);
        assertTrue(result.valid());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void validateItem_invalidKind() {
        ValidationRequest request = new ValidationRequest(null, "INVALID", "MED-001", null, null);
        ValidationResult result = validationService.validateItem(request);
        assertFalse(result.valid());
        assertEquals(1, result.issues().size());
        assertEquals("INVALID_KIND", result.issues().get(0).code());
    }

    @Test
    void validateItem_codeTooLong() {
        String longCode = "A".repeat(101);
        ValidationRequest request = new ValidationRequest(null, "PRODUCT", longCode, null, null);
        ValidationResult result = validationService.validateItem(request);
        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(i -> "CODE_TOO_LONG".equals(i.code())));
    }

    @Test
    void validateItem_validRestrictions() {
        Map<String, Object> restrictions = Map.of(
                "prescription_required", Map.of("enabled", true, "enforcement_hint", "BLOCK"),
                "cold_chain_required", Map.of("enabled", true)
        );
        ValidationRequest request = new ValidationRequest(null, "PRODUCT", "MED-001", restrictions, null);
        ValidationResult result = validationService.validateItem(request);
        assertTrue(result.valid());
    }

    @Test
    void validateItem_unknownRestriction_warns() {
        Map<String, Object> restrictions = Map.of("unknown_flag", Map.of("enabled", true));
        ValidationRequest request = new ValidationRequest(null, "PRODUCT", "MED-001", restrictions, null);
        ValidationResult result = validationService.validateItem(request);
        assertTrue(result.valid()); // warnings don't fail validation
        assertTrue(result.issues().stream().anyMatch(i -> "UNKNOWN_RESTRICTION".equals(i.code())));
    }

    @Test
    void validateItem_ziboBindings_missingFields() {
        Object[] bindings = new Object[]{ Map.of("system", "http://zibo/cs") }; // missing 'code'
        ValidationRequest request = new ValidationRequest(null, "PRODUCT", "MED-001", null, bindings);
        ValidationResult result = validationService.validateItem(request);
        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(i -> "MISSING_BINDING_FIELDS".equals(i.code())));
    }

    @Test
    void validateItem_ziboBindings_valid() {
        Object[] bindings = new Object[]{ Map.of("system", "http://zibo/cs", "code", "PARA-001") };
        ValidationRequest request = new ValidationRequest(null, "PRODUCT", "MED-001", null, bindings);
        ValidationResult result = validationService.validateItem(request);
        assertTrue(result.valid());
    }

    // ── validatePack (real checks) ────────────────────────────────────────────

    private CatalogItemEntity packItem(String id, String catalogId, String code, String restrictions) {
        CatalogItemEntity item = new CatalogItemEntity();
        item.setItemId(id);
        item.setCatalogId(catalogId);
        item.setKind("PRODUCT");
        item.setCanonicalCode(code);
        item.setDisplayName(code);
        item.setRestrictions(restrictions);
        item.setActive(true);
        return item;
    }

    private CatalogEntity catalog(String id, String status) {
        CatalogEntity c = new CatalogEntity();
        c.setCatalogId(id);
        c.setStatus(status);
        return c;
    }

    @Test
    void validatePack_invalidKind_failsFast() {
        ValidationResult result = validationService.validatePack("BOGUS", null, null);
        assertFalse(result.valid());
        assertEquals("INVALID_KIND", result.issues().get(0).code());
    }

    @Test
    void validatePack_explicitItems_missingItem_isError() {
        when(itemRepository.findById("NOPE")).thenReturn(Optional.empty());
        ValidationResult result = validationService.validatePack(null, null, List.of("NOPE"));
        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(i -> "ITEM_NOT_FOUND".equals(i.code())));
    }

    @Test
    void validatePack_explicitItems_unpublishedCatalog_isError() {
        CatalogItemEntity item = packItem("I1", "CAT1", "C-1", "{}");
        when(itemRepository.findById("I1")).thenReturn(Optional.of(item));
        when(catalogRepository.findById("CAT1")).thenReturn(Optional.of(catalog("CAT1", "DRAFT")));
        ValidationResult result = validationService.validatePack(null, null, List.of("I1"));
        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(i -> "NOT_IN_PUBLISHED_CATALOG".equals(i.code())));
    }

    @Test
    void validatePack_explicitItems_controlledWithoutPrescription_isComposeError() {
        CatalogItemEntity item = packItem("I1", "CAT1", "C-1",
                "{\"controlled_item\": {\"enabled\": true}}");
        when(itemRepository.findById("I1")).thenReturn(Optional.of(item));
        when(catalogRepository.findById("CAT1")).thenReturn(Optional.of(catalog("CAT1", "PUBLISHED")));
        ValidationResult result = validationService.validatePack(null, null, List.of("I1"));
        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(i -> "RESTRICTION_COMPOSE".equals(i.code())));
    }

    @Test
    void validatePack_explicitItems_coherentItem_passes() {
        CatalogItemEntity item = packItem("I1", "CAT1", "C-1",
                "{\"controlled_item\": {\"enabled\": true}, \"prescription_required\": {\"enabled\": true}}");
        when(itemRepository.findById("I1")).thenReturn(Optional.of(item));
        when(catalogRepository.findById("CAT1")).thenReturn(Optional.of(catalog("CAT1", "PUBLISHED")));
        ValidationResult result = validationService.validatePack(null, null, List.of("I1"));
        assertTrue(result.valid());
    }

    @Test
    void validatePack_wholePack_duplicateCanonicalCodes_isError() {
        CatalogItemEntity a = packItem("I1", "CAT1", "DUP-1", "{}");
        CatalogItemEntity b = packItem("I2", "CAT2", "DUP-1", "{}");
        when(itemRepository.findPublishedItems(isNull(), isNull(), isNull(), isNull(), isNull(),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(a, b)));
        when(catalogRepository.findById("CAT1")).thenReturn(Optional.of(catalog("CAT1", "PUBLISHED")));
        when(catalogRepository.findById("CAT2")).thenReturn(Optional.of(catalog("CAT2", "PUBLISHED")));
        ValidationResult result = validationService.validatePack(null, null, null);
        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(i -> "DUPLICATE_CANONICAL_CODE".equals(i.code())));
    }

    // ── risk-friction lookup ──────────────────────────────────────────────────

    @Test
    void riskFriction_knownClass_returnsMappingRow() {
        RiskFrictionMappingEntity m = new RiskFrictionMappingEntity();
        m.setRiskClassification("REGULATED");
        m.setFrictionLevel("MAXIMUM");
        m.setRequiresProvider(true);
        m.setRequiresFacility(true);
        m.setMaxQtyPerOrder(10);
        when(riskFrictionRepository.findById("REGULATED")).thenReturn(Optional.of(m));

        RiskFrictionView view = validationService.riskFriction("REGULATED");
        assertEquals("MAXIMUM", view.frictionLevel());
        assertTrue(view.requiresProvider());
        assertTrue(view.requiresFacility());
        assertEquals(10, view.maxQtyPerOrder());
    }

    @Test
    void riskFriction_unknownClass_throwsNotFound() {
        when(riskFrictionRepository.findById("NOT_A_CLASS")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> validationService.riskFriction("NOT_A_CLASS"));
    }
}
