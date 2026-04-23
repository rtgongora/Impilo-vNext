package zw.gov.mohcc.impilo.mushex.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.mushex.api.dto.FinancialPlatformCardRequests;
import zw.gov.mohcc.impilo.mushex.domain.entity.CardProfileEntity;
import zw.gov.mohcc.impilo.mushex.service.FinancialCardReversalPlatformService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/mushex/v1/platform/card-profiles")
public class CardProfilePlatformController {

    private final FinancialCardReversalPlatformService service;

    public CardProfilePlatformController(FinancialCardReversalPlatformService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CardProfileEntity>>> list(@RequestParam(required = false) String walletId) {
        var ctx = TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(service.listCardProfiles(walletId), ctx.correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CardProfileEntity>> create(
            @Valid @RequestBody FinancialPlatformCardRequests.CreateCardProfileRequest request) {
        var ctx = TrustContextHolder.require();
        CardProfileEntity saved = service.createCardProfile(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(saved, ctx.correlationId().toString()));
    }
}
