package zw.gov.mohcc.impilo.pharmacy.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pharmacy.api.dto.DispenseItemDto;
import zw.gov.mohcc.impilo.pharmacy.api.dto.SubstituteRequest;
import zw.gov.mohcc.impilo.pharmacy.api.dto.SubstitutionOptionDto;
import zw.gov.mohcc.impilo.pharmacy.core.SubstitutionEngine;
import zw.gov.mohcc.impilo.pharmacy.domain.SubstitutionRuleType;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseItemEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for drug substitution operations on dispense items.
 *
 * <p>Provides endpoints for viewing available substitution options
 * and applying a substitution to an individual dispense item.</p>
 */
@RestController
@RequestMapping("/v1/dispense-items")
public class SubstitutionController {

    private static final Logger log = LoggerFactory.getLogger(SubstitutionController.class);

    private final SubstitutionEngine substitutionEngine;

    public SubstitutionController(SubstitutionEngine substitutionEngine) {
        this.substitutionEngine = substitutionEngine;
    }

    /**
     * Apply a drug substitution to a dispense item.
     */
    @PostMapping("/{itemId}/substitute")
    public ResponseEntity<ApiResponse<DispenseItemDto>> applySubstitution(
            @PathVariable UUID itemId,
            @Valid @RequestBody SubstituteRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        DispenseItemEntity item = substitutionEngine.applySubstitution(
                itemId, request.targetDrugCode(), request.reason());

        log.info("Substitution applied: itemId={}, targetDrug={}", itemId, request.targetDrugCode());

        return ResponseEntity.ok(ApiResponse.ok(DispenseItemDto.from(item), correlationId));
    }

    /**
     * Get available substitution options for a dispense item.
     */
    @GetMapping("/{itemId}/substitution-options")
    public ResponseEntity<ApiResponse<List<SubstitutionOptionDto>>> getSubstitutionOptions(
            @PathVariable UUID itemId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<SubstitutionOptionDto> options = substitutionEngine.getSubstitutionOptions(itemId).stream()
                .map(opt -> new SubstitutionOptionDto(
                        opt.drugCode(),
                        opt.drugDisplay(),
                        SubstitutionRuleType.valueOf(opt.ruleType()),
                        opt.reason(),
                        opt.requiresStepUp()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(options, correlationId));
    }
}
