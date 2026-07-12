package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.api.dto.CartDtos;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.integration.MsikaListingClient;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.CartEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.CartItemEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.CartItemRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.CartRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderStateMachine stateMachine;
    private final CatalogValidationService catalogValidationService;
    private final MsikaListingClient msikaListingClient;
    private final ObjectMapper objectMapper;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       OrderStateMachine stateMachine,
                       CatalogValidationService catalogValidationService,
                       MsikaListingClient msikaListingClient,
                       ObjectMapper objectMapper) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.stateMachine = stateMachine;
        this.catalogValidationService = catalogValidationService;
        this.msikaListingClient = msikaListingClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CartDtos.CartView getOrCreateOpenCart(UUID tenantId, String actorId, ActorType actorType, String patientCpid, String channel) {
        CartEntity cart = cartRepository.findByTenantIdAndActorIdAndStatus(tenantId, actorId, CartStatus.OPEN)
                .orElseGet(() -> {
                    CartEntity c = new CartEntity();
                    c.setCartId(UlidGenerator.generate());
                    c.setTenantId(tenantId);
                    c.setActorId(actorId);
                    c.setActorType(actorType);
                    c.setPatientCpid(patientCpid);
                    if (channel != null) c.setChannel(channel);
                    c.setStatus(CartStatus.OPEN);
                    return cartRepository.save(c);
                });
        List<CartItemEntity> items = cartItemRepository.findByCartIdOrderByUpdatedAtDesc(cart.getCartId());
        return toView(cart, items);
    }

    @Transactional
    public CartDtos.CartView addItem(String cartId, CartDtos.AddCartItemRequest req) {
        CartEntity cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found: " + cartId));
        if (cart.getStatus() != CartStatus.OPEN) throw new IllegalStateException("Cart not OPEN");

        CartItemEntity item = new CartItemEntity();
        item.setId(UlidGenerator.generate());
        item.setCartId(cartId);
        item.setMsikaCoreCode(req.msikaCoreCode());
        item.setListingId(req.listingId());
        if (req.kind() != null) item.setKind(LineItemKind.valueOf(req.kind()));
        item.setQty(Math.max(1, req.qty()));
        if (req.fulfillmentMode() != null) item.setFulfillmentMode(FulfillmentMode.valueOf(req.fulfillmentMode()));
        item.setMetadata(JsonSupport.toJsonSafe(objectMapper, req.metadata(), null));
        cartItemRepository.save(item);

        List<CartItemEntity> items = cartItemRepository.findByCartIdOrderByUpdatedAtDesc(cartId);
        return toView(cart, items);
    }

    @Transactional
    public CartDtos.CartView removeItem(String cartId, String msikaCoreCode) {
        CartEntity cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found: " + cartId));
        if (cart.getStatus() != CartStatus.OPEN) throw new IllegalStateException("Cart not OPEN");
        cartItemRepository.deleteByCartIdAndMsikaCoreCode(cartId, msikaCoreCode);
        List<CartItemEntity> items = cartItemRepository.findByCartIdOrderByUpdatedAtDesc(cartId);
        return toView(cart, items);
    }

    /**
     * Real checkout: (1) catalog-validation pre-order gate over every line, (2)
     * server-side price resolution per line via {@link MsikaListingClient} — the
     * client-supplied cart quantity/code is trusted, price never is — and only then
     * (3) order + priced lines are created. Any pricing/validation failure throws a
     * 422 {@link ValidationFailedException} with per-line detail and creates nothing.
     */
    @Transactional
    public CartDtos.CheckoutResponse checkout(String cartId, CartDtos.CheckoutRequest req) {
        CartEntity cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found: " + cartId));
        if (cart.getStatus() != CartStatus.OPEN) throw new IllegalStateException("Cart not OPEN");

        List<CartItemEntity> items = cartItemRepository.findByCartIdOrderByUpdatedAtDesc(cartId);
        if (items.isEmpty()) throw new IllegalStateException("Cart is empty");

        List<CatalogValidationService.CartItem> validationItems = items.stream()
                .map(i -> new CatalogValidationService.CartItem(i.getMsikaCoreCode(), i.getQty()))
                .toList();
        CatalogValidationService.ValidationResult validation = catalogValidationService.validateCart(
                validationItems, cart.getChannel(), cart.getActorType(), cart.getPatientCpid(), null, null);
        if (!validation.valid()) {
            throw new ValidationFailedException("CATALOG_VALIDATION_FAILED",
                    "One or more cart items failed catalog validation", validation.items());
        }

        List<PricedItem> priced = new ArrayList<>();
        List<Map<String, Object>> pricingErrors = new ArrayList<>();
        for (CartItemEntity i : items) {
            if (i.getListingId() == null || i.getListingId().isBlank()) {
                pricingErrors.add(pricingError(i.getMsikaCoreCode(), null, "Missing listingId — cannot resolve price"));
                continue;
            }
            Optional<MsikaListingClient.ListingPrice> price = msikaListingClient.resolvePrice(i.getListingId());
            if (price.isEmpty()) {
                pricingErrors.add(pricingError(i.getMsikaCoreCode(), i.getListingId(), "Listing not found or unpriced"));
                continue;
            }
            priced.add(new PricedItem(i, price.get()));
        }
        if (!pricingErrors.isEmpty()) {
            throw new ValidationFailedException("PRICING_FAILED",
                    "One or more cart items could not be priced", pricingErrors);
        }

        OrderType orderType = req.orderType() != null ? OrderType.valueOf(req.orderType()) : OrderType.OTC_PRODUCT_ORDER;
        // The order's payee is the seller of the first priced listing —
        // carried as vendorRef (a marketplace seller id is a string, not the
        // UUID vendorId slot) so a citizen-buyer order (no facility context)
        // still resolves a MusheX payee.
        String payeeVendorRef = priced.stream()
                .map(p -> p.price().sellerId())
                .filter(s -> s != null && !s.isBlank())
                .findFirst().orElse(null);
        OrderEntity order = stateMachine.createOrder(
                cart.getTenantId(),
                cart.getActorId(),
                cart.getActorType(),
                cart.getPatientCpid(),
                orderType,
                req.facilityId(),
                null,
                req.vendorId(),
                payeeVendorRef,
                null,
                req.idempotencyKey());

        for (PricedItem p : priced) {
            CartItemEntity i = p.item();
            MsikaListingClient.ListingPrice price = p.price();
            // Snapshot the server-resolved price back onto the cart line for display parity.
            i.setUnitPrice(price.amount());
            i.setCurrency(price.currency());
            cartItemRepository.save(i);

            stateMachine.addLine(order.getOrderId(), i.getMsikaCoreCode(), i.getKind(),
                    i.getQty(), price.amount(), i.getFulfillmentMode(),
                    null, null, i.getMetadata());
        }

        cart.setStatus(CartStatus.CHECKED_OUT);
        cartRepository.save(cart);

        return new CartDtos.CheckoutResponse(cartId, order.getOrderId(), order.getStatus().name());
    }

    private static Map<String, Object> pricingError(String msikaCoreCode, String listingId, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("msikaCoreCode", msikaCoreCode);
        m.put("listingId", listingId);
        m.put("error", error);
        return m;
    }

    private record PricedItem(CartItemEntity item, MsikaListingClient.ListingPrice price) {}

    private CartDtos.CartView toView(CartEntity cart, List<CartItemEntity> items) {
        List<CartDtos.CartItemView> itemViews = items.stream().map(i -> new CartDtos.CartItemView(
                i.getId(),
                i.getMsikaCoreCode(),
                i.getKind().name(),
                i.getQty(),
                i.getFulfillmentMode() != null ? i.getFulfillmentMode().name() : null,
                JsonSupport.parseJsonSafe(objectMapper, i.getMetadata(), null),
                i.getCreatedAt(),
                i.getUpdatedAt(),
                i.getListingId(),
                i.getUnitPrice(),
                i.getCurrency()
        )).toList();
        return new CartDtos.CartView(
                cart.getCartId(),
                cart.getTenantId(),
                cart.getActorId(),
                cart.getActorType().name(),
                cart.getPatientCpid(),
                cart.getChannel(),
                cart.getStatus().name(),
                JsonSupport.parseJsonSafe(objectMapper, cart.getMetadata(), null),
                itemViews,
                cart.getCreatedAt(),
                cart.getUpdatedAt()
        );
    }
}

