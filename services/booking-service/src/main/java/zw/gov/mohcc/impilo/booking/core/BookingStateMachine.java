package zw.gov.mohcc.impilo.booking.core;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.booking.domain.BookingStatus;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

@Component
public class BookingStateMachine {

    private static final Map<BookingStatus, Set<BookingStatus>> TRANSITIONS = new EnumMap<>(BookingStatus.class);

    static {
        TRANSITIONS.put(BookingStatus.REQUESTED, EnumSet.of(
                BookingStatus.TRIAGED, BookingStatus.PENDING_APPROVAL, BookingStatus.PENDING_CONSENT,
                BookingStatus.CANCELLED, BookingStatus.REDIRECTED));
        TRANSITIONS.put(BookingStatus.TRIAGED, EnumSet.of(
                BookingStatus.PENDING_APPROVAL, BookingStatus.PENDING_CONSENT, BookingStatus.PENDING_PAYMENT,
                BookingStatus.RESERVED, BookingStatus.CANCELLED, BookingStatus.REDIRECTED));
        TRANSITIONS.put(BookingStatus.PENDING_APPROVAL, EnumSet.of(
                BookingStatus.APPROVED, BookingStatus.REJECTED, BookingStatus.CANCELLED));
        TRANSITIONS.put(BookingStatus.PENDING_CONSENT, EnumSet.of(
                BookingStatus.RESERVED, BookingStatus.CANCELLED));
        TRANSITIONS.put(BookingStatus.PENDING_PAYMENT, EnumSet.of(
                BookingStatus.RESERVED, BookingStatus.CANCELLED));
        TRANSITIONS.put(BookingStatus.RESERVED, EnumSet.of(
                BookingStatus.CONFIRMED, BookingStatus.CANCELLED));
        TRANSITIONS.put(BookingStatus.APPROVED, EnumSet.of(
                BookingStatus.CONFIRMED, BookingStatus.RESERVED, BookingStatus.CANCELLED));
        TRANSITIONS.put(BookingStatus.CONFIRMED, EnumSet.of(
                BookingStatus.FULFILLED, BookingStatus.CANCELLED));
        TRANSITIONS.put(BookingStatus.REDIRECTED, EnumSet.of(
                BookingStatus.REQUESTED, BookingStatus.CANCELLED));
        TRANSITIONS.put(BookingStatus.REJECTED, EnumSet.noneOf(BookingStatus.class));
        TRANSITIONS.put(BookingStatus.CANCELLED, EnumSet.noneOf(BookingStatus.class));
        TRANSITIONS.put(BookingStatus.FULFILLED, EnumSet.noneOf(BookingStatus.class));
    }

    private final BookingAuditService auditService;

    public BookingStateMachine(BookingAuditService auditService) {
        this.auditService = auditService;
    }

    public BookingEntity transition(TrustContext ctx, BookingEntity booking, BookingStatus target, String action) {
        BookingStatus current = booking.getBookingStatus();
        if (current == target) {
            return booking;
        }
        Set<BookingStatus> allowed = TRANSITIONS.getOrDefault(current, EnumSet.noneOf(BookingStatus.class));
        if (!allowed.contains(target)) {
            throw new IllegalStateException("Invalid booking transition " + current + " -> " + target);
        }
        booking.setBookingStatus(target);
        booking.setUpdatedBy(ctx.actorId());
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("action", action);
        auditService.record(ctx, booking.getId(), action, current, target, detail);
        return booking;
    }

    public boolean canTransition(BookingStatus from, BookingStatus to) {
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(BookingStatus.class)).contains(to);
    }
}
