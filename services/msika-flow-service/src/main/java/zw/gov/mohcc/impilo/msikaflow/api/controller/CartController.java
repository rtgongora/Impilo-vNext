package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.*;
import zw.gov.mohcc.impilo.msikaflow.core.CatalogValidationService;
import zw.gov.mohcc.impilo.msikaflow.domain.ActorType;

import java.util.List;

@RestController
@RequestMapping("/v1/cart")
public class CartController {

    private final CatalogValidationService catalogValidation;

    public CartController(CatalogValidationService catalogValidation) {
        this.catalogValidation = catalogValidation;
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<CatalogValidationService.ValidationResult>> validateCart(
            @Valid @RequestBody ValidateCartRequest req, HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        String actorTypeStr = TrustHeaderExtractor.actorType(httpReq);
        ActorType actorType = ActorType.valueOf(actorTypeStr);
        String patientCpid = httpReq.getHeader("X-Patient-Cpid");

        List<CatalogValidationService.CartItem> items = req.items().stream()
                .map(i -> new CatalogValidationService.CartItem(i.msikaCoreCode(), i.qty()))
                .toList();

        CatalogValidationService.ValidationResult result = catalogValidation.validateCart(
                items, req.channel(), actorType, patientCpid);

        return ResponseEntity.ok(ApiResponse.ok(result, correlationId));
    }
}
