package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.api.dto.CartDtos;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.CartEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.CartItemEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.CartItemRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.CartRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderStateMachine stateMachine;
    private final ObjectMapper objectMapper;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       OrderStateMachine stateMachine,
                       ObjectMapper objectMapper) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.stateMachine = stateMachine;
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

    @Transactional
    public CartDtos.CheckoutResponse checkout(String cartId, CartDtos.CheckoutRequest req) {
        CartEntity cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new IllegalArgumentException("Cart not found: " + cartId));
        if (cart.getStatus() != CartStatus.OPEN) throw new IllegalStateException("Cart not OPEN");

        List<CartItemEntity> items = cartItemRepository.findByCartIdOrderByUpdatedAtDesc(cartId);
        if (items.isEmpty()) throw new IllegalStateException("Cart is empty");

        OrderType orderType = req.orderType() != null ? OrderType.valueOf(req.orderType()) : OrderType.OTC_PRODUCT_ORDER;
        OrderEntity order = stateMachine.createOrder(cart.getTenantId(), cart.getActorId(), cart.getActorType(),
                cart.getPatientCpid(), orderType, req.facilityId(), req.vendorId(), req.idempotencyKey());

        for (CartItemEntity i : items) {
            stateMachine.addLine(order.getOrderId(), i.getMsikaCoreCode(), i.getKind(),
                    i.getQty(), BigDecimal.ZERO, i.getFulfillmentMode(),
                    null, null, i.getMetadata());
        }

        cart.setStatus(CartStatus.CHECKED_OUT);
        cartRepository.save(cart);

        return new CartDtos.CheckoutResponse(cartId, order.getOrderId(), order.getStatus().name());
    }

    private CartDtos.CartView toView(CartEntity cart, List<CartItemEntity> items) {
        List<CartDtos.CartItemView> itemViews = items.stream().map(i -> new CartDtos.CartItemView(
                i.getId(),
                i.getMsikaCoreCode(),
                i.getKind().name(),
                i.getQty(),
                i.getFulfillmentMode() != null ? i.getFulfillmentMode().name() : null,
                JsonSupport.parseJsonSafe(objectMapper, i.getMetadata(), null),
                i.getCreatedAt(),
                i.getUpdatedAt()
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

