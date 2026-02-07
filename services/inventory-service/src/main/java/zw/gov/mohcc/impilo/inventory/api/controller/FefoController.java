package zw.gov.mohcc.impilo.inventory.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.inventory.api.dto.FefoSuggestRequest;
import zw.gov.mohcc.impilo.inventory.api.dto.FefoSuggestionDto;
import zw.gov.mohcc.impilo.inventory.core.FefoService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for FEFO (First-Expiry-First-Out) pick suggestions.
 *
 * <p>Generates optimal batch picks by selecting from on-hand stock in
 * ascending expiry order, ensuring the earliest-expiring batches are
 * consumed first to minimize wastage.</p>
 */
@RestController
@RequestMapping("/v1/fefo")
public class FefoController {

    private static final Logger log = LoggerFactory.getLogger(FefoController.class);

    private final FefoService fefoService;

    public FefoController(FefoService fefoService) {
        this.fefoService = fefoService;
    }

    /**
     * Generate FEFO pick suggestions for the requested item and quantity.
     */
    @PostMapping("/suggest")
    public ResponseEntity<ApiResponse<List<FefoSuggestionDto>>> suggestPick(
            @Valid @RequestBody FefoSuggestRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<FefoService.FefoSuggestion> suggestions = fefoService.suggestPick(
                request.facilityId(), request.storeId(), request.itemCode(), request.qtyNeeded());

        List<FefoSuggestionDto> dtos = suggestions.stream()
                .map(FefoSuggestionDto::from)
                .collect(Collectors.toList());

        log.info("FEFO suggestion: itemCode={}, qtyNeeded={}, batches={}",
                request.itemCode(), request.qtyNeeded(), dtos.size());

        return ResponseEntity.ok(ApiResponse.ok(dtos, correlationId));
    }
}
