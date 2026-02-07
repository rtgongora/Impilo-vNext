package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.msika.api.dto.ValidationRequest;
import zw.gov.mohcc.impilo.msika.api.dto.ValidationResult;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogItemRepository;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {

    @Mock private CatalogItemRepository itemRepository;
    private ValidationService validationService;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        validationService = new ValidationService(itemRepository, objectMapper);
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
}
