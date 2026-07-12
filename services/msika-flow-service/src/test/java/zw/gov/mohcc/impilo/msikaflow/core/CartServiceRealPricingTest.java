package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.msikaflow.api.dto.CartDtos;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.integration.MsikaListingClient;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.CartEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.CartItemEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.CartItemRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.CartRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Locks the checkout pricing rewrite: prices are resolved server-side from
 * msika-service listings (never {@code BigDecimal.ZERO}), and an unpriced/missing
 * listing is a 422 that creates no order.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartServiceRealPricingTest {

    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock OrderStateMachine stateMachine;
    @Mock CatalogValidationService catalogValidationService;
    @Mock MsikaListingClient msikaListingClient;

    CartService service;

    private static final String CART = "CART12345678901234567890";

    @BeforeEach
    void setUp() {
        service = new CartService(cartRepository, cartItemRepository, stateMachine,
                catalogValidationService, msikaListingClient, new ObjectMapper());
    }

    private CartEntity openCart() {
        CartEntity cart = new CartEntity();
        cart.setCartId(CART);
        cart.setTenantId(UUID.randomUUID());
        cart.setActorId("actor-1");
        cart.setActorType(ActorType.PATIENT);
        cart.setStatus(CartStatus.OPEN);
        return cart;
    }

    private CartItemEntity item(String code, String listingId) {
        CartItemEntity i = new CartItemEntity();
        i.setId(UlidGenerator.generate());
        i.setCartId(CART);
        i.setMsikaCoreCode(code);
        i.setListingId(listingId);
        i.setKind(LineItemKind.PRODUCT);
        i.setQty(2);
        return i;
    }

    @Test
    void checkout_pricesLinesFromListing_notZero() {
        CartEntity cart = openCart();
        CartItemEntity i = item("MED-1", "LST-1");
        when(cartRepository.findById(CART)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByUpdatedAtDesc(CART)).thenReturn(List.of(i));
        when(catalogValidationService.validateCart(any(), any(), any(), any(), any(), any()))
                .thenReturn(new CatalogValidationService.ValidationResult(true, List.of()));
        when(msikaListingClient.resolvePrice("LST-1"))
                .thenReturn(Optional.of(new MsikaListingClient.ListingPrice(new BigDecimal("12.50"), "ZWG")));
        OrderEntity order = new OrderEntity();
        order.setOrderId("ORDER12345678901234567");
        order.setStatus(OrderStatus.CREATED);
        when(stateMachine.createOrder(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(order);

        CartDtos.CheckoutResponse resp = service.checkout(CART,
                new CartDtos.CheckoutRequest("OTC_PRODUCT_ORDER", null, null, "key-1"));

        assertEquals("ORDER12345678901234567", resp.orderId());
        ArgumentCaptor<BigDecimal> priceCap = ArgumentCaptor.forClass(BigDecimal.class);
        verify(stateMachine).addLine(eq("ORDER12345678901234567"), eq("MED-1"), eq(LineItemKind.PRODUCT),
                eq(2), priceCap.capture(), any(), any(), any(), any());
        assertEquals(new BigDecimal("12.50"), priceCap.getValue());
        assertNotEquals(BigDecimal.ZERO, priceCap.getValue());
    }

    @Test
    void checkout_unpricedListing_throws422_noOrderCreated() {
        CartEntity cart = openCart();
        CartItemEntity i = item("MED-1", "LST-MISSING");
        when(cartRepository.findById(CART)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByUpdatedAtDesc(CART)).thenReturn(List.of(i));
        when(catalogValidationService.validateCart(any(), any(), any(), any(), any(), any()))
                .thenReturn(new CatalogValidationService.ValidationResult(true, List.of()));
        when(msikaListingClient.resolvePrice("LST-MISSING")).thenReturn(Optional.empty());

        assertThrows(ValidationFailedException.class, () -> service.checkout(CART,
                new CartDtos.CheckoutRequest("OTC_PRODUCT_ORDER", null, null, "key-1")));
        verify(stateMachine, never()).createOrder(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void checkout_missingListingId_throws422() {
        CartEntity cart = openCart();
        CartItemEntity i = item("MED-1", null);
        when(cartRepository.findById(CART)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByUpdatedAtDesc(CART)).thenReturn(List.of(i));
        when(catalogValidationService.validateCart(any(), any(), any(), any(), any(), any()))
                .thenReturn(new CatalogValidationService.ValidationResult(true, List.of()));

        assertThrows(ValidationFailedException.class, () -> service.checkout(CART,
                new CartDtos.CheckoutRequest("OTC_PRODUCT_ORDER", null, null, "key-1")));
        verify(stateMachine, never()).createOrder(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void checkout_catalogValidationFails_throws422() {
        CartEntity cart = openCart();
        CartItemEntity i = item("MED-1", "LST-1");
        when(cartRepository.findById(CART)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdOrderByUpdatedAtDesc(CART)).thenReturn(List.of(i));
        when(catalogValidationService.validateCart(any(), any(), any(), any(), any(), any()))
                .thenReturn(new CatalogValidationService.ValidationResult(false,
                        List.of(new CatalogValidationService.ItemValidation("MED-1", false, List.of("blocked"), null))));

        assertThrows(ValidationFailedException.class, () -> service.checkout(CART,
                new CartDtos.CheckoutRequest("OTC_PRODUCT_ORDER", null, null, "key-1")));
        verify(msikaListingClient, never()).resolvePrice(any());
    }
}
