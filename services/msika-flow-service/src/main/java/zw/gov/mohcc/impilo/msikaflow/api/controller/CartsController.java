package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.ApiResponse;
import zw.gov.mohcc.impilo.msikaflow.api.dto.CartDtos;
import zw.gov.mohcc.impilo.msikaflow.core.CartService;
import zw.gov.mohcc.impilo.msikaflow.domain.ActorType;

import java.util.UUID;

@RestController
@RequestMapping("/v1/carts")
public class CartsController {

    private final CartService cartService;

    public CartsController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/open")
    public ResponseEntity<ApiResponse<CartDtos.CartView>> getOrCreateOpen(HttpServletRequest httpReq,
                                                                         @RequestParam(required = false) String patientCpid,
                                                                         @RequestParam(defaultValue = "WEB") String channel) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        ActorType actorType = ActorType.valueOf(TrustHeaderExtractor.actorType(httpReq));
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        CartDtos.CartView view = cartService.getOrCreateOpenCart(tenantId, actorId, actorType, patientCpid, channel);
        return ResponseEntity.ok(ApiResponse.ok(view, correlationId));
    }

    @PostMapping("/{cartId}/items")
    public ResponseEntity<ApiResponse<CartDtos.CartView>> addItem(@PathVariable String cartId,
                                                                  @Valid @RequestBody CartDtos.AddCartItemRequest req,
                                                                  HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(cartService.addItem(cartId, req), correlationId));
    }

    @DeleteMapping("/{cartId}/items")
    public ResponseEntity<ApiResponse<CartDtos.CartView>> removeItem(@PathVariable String cartId,
                                                                     @RequestParam String msikaCoreCode,
                                                                     HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(cartService.removeItem(cartId, msikaCoreCode), correlationId));
    }

    @PostMapping("/{cartId}/checkout")
    public ResponseEntity<ApiResponse<CartDtos.CheckoutResponse>> checkout(@PathVariable String cartId,
                                                                           @Valid @RequestBody CartDtos.CheckoutRequest req,
                                                                           HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        return ResponseEntity.ok(ApiResponse.ok(cartService.checkout(cartId, req), correlationId));
    }
}

