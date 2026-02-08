package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.*;
import zw.gov.mohcc.impilo.msikaflow.core.BookingService;
import zw.gov.mohcc.impilo.msikaflow.domain.ActorType;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OrderEntity;

import java.util.UUID;

@RestController
@RequestMapping("/v1/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
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
