package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.*;
import zw.gov.mohcc.impilo.msikaflow.core.BookingService;
import zw.gov.mohcc.impilo.msikaflow.core.OrderStateMachine;
import zw.gov.mohcc.impilo.msikaflow.domain.ActorType;
import zw.gov.mohcc.impilo.msikaflow.domain.OrderType;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.OrderRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final OrderRepository orderRepository;
    private final OrderStateMachine stateMachine;

    public BookingController(BookingService bookingService,
                             OrderRepository orderRepository,
                             OrderStateMachine stateMachine) {
        this.bookingService = bookingService;
        this.orderRepository = orderRepository;
        this.stateMachine = stateMachine;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> listBookings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpReq) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        Page<OrderEntity> bookings = orderRepository.findByTenantIdAndOrderType(
                tenantId, OrderType.SERVICE_BOOKING_ORDER, pageable);
        List<OrderView> items = bookings.getContent().stream()
                .map(order -> OrderView.from(order, stateMachine.getOrderLines(order.getOrderId())))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(new Object() {
            public final List<OrderView> items = items;
            public final int page = bookings.getNumber();
            public final int size = bookings.getSize();
            public final long total_elements = bookings.getTotalElements();
        }, correlationId));
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<OrderView>> createBooking(
            @Valid @RequestBody BookingCreateRequest req, HttpServletRequest httpReq) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        ActorType actorType = ActorType.valueOf(TrustHeaderExtractor.actorType(httpReq));
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        OrderEntity order = bookingService.createBooking(tenantId, actorId, actorType,
                req.patientCpid(), req.facilityId(), req.idempotencyKey());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(OrderView.from(order, null), correlationId));
    }

    @PostMapping("/{id}/reschedule")
    public ResponseEntity<ApiResponse<OrderView>> reschedule(
            @PathVariable String id, HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String actorType = TrustHeaderExtractor.actorType(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        OrderEntity order = bookingService.rescheduleBooking(id, actorId, actorType, null);
        return ResponseEntity.ok(ApiResponse.ok(OrderView.from(order, null), correlationId));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<OrderView>> cancelBooking(
            @PathVariable String id, HttpServletRequest httpReq) {
        String actorId = TrustHeaderExtractor.actorId(httpReq);
        String actorType = TrustHeaderExtractor.actorType(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);

        OrderEntity order = bookingService.cancelBooking(id, actorId, actorType, "USER_CANCELLED");
        return ResponseEntity.ok(ApiResponse.ok(OrderView.from(order, null), correlationId));
    }
}
