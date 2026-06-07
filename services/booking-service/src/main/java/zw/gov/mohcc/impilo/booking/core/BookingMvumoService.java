package zw.gov.mohcc.impilo.booking.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.booking.domain.ConsentStatus;
import zw.gov.mohcc.impilo.booking.domain.MvumoType;
import zw.gov.mohcc.impilo.booking.integration.MvumoClient;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingEntity;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingMvumoEntity;
import zw.gov.mohcc.impilo.booking.persistence.repository.BookingMvumoRepository;
import zw.gov.mohcc.impilo.booking.persistence.repository.BookingRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingMvumoService {

    private final BookingRepository bookingRepository;
    private final BookingMvumoRepository mvumoRepository;
    private final MvumoClient mvumoClient;

    public BookingMvumoService(BookingRepository bookingRepository,
                               BookingMvumoRepository mvumoRepository,
                               MvumoClient mvumoClient) {
        this.bookingRepository = bookingRepository;
        this.mvumoRepository = mvumoRepository;
        this.mvumoClient = mvumoClient;
    }

    @Transactional
    public BookingMvumoEntity requestConsent(UUID bookingId, MvumoType type) {
        TrustContext ctx = TrustContextHolder.require();
        BookingEntity booking = requireBooking(bookingId, ctx.tenantId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("subjectId", booking.getClientId());
        payload.put("bookingId", booking.getId().toString());
        payload.put("bookingNumber", booking.getBookingNumber());
        payload.put("mvumoType", type.name());

        Map<String, Object> response = mvumoClient.createConsentRequest(ctx, payload);
        String recordId = stringOrDefault(response.get("id"), UUID.randomUUID().toString());

        BookingMvumoEntity entity = mvumoRepository.findByBookingIdAndMvumoType(bookingId, type)
                .orElseGet(BookingMvumoEntity::new);
        entity.setTenantId(ctx.tenantId());
        entity.setBookingId(bookingId);
        entity.setMvumoRecordId(recordId);
        entity.setMvumoType(type);
        entity.setStatus(ConsentStatus.PENDING);
        entity = mvumoRepository.save(entity);

        booking.setConsentStatus(ConsentStatus.PENDING);
        bookingRepository.save(booking);
        return entity;
    }

    @Transactional
    public BookingMvumoEntity grant(UUID bookingId, MvumoType type) {
        TrustContext ctx = TrustContextHolder.require();
        BookingMvumoEntity entity = requireMvumo(bookingId, type);
        mvumoClient.grant(ctx, entity.getMvumoRecordId(), Map.of("bookingId", bookingId.toString()));
        entity.setStatus(ConsentStatus.GRANTED);
        entity = mvumoRepository.save(entity);
        syncBookingConsent(bookingId, ctx.tenantId());
        return entity;
    }

    @Transactional
    public BookingMvumoEntity refuse(UUID bookingId, MvumoType type, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        BookingMvumoEntity entity = requireMvumo(bookingId, type);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", reason != null ? reason : "Refused");
        mvumoClient.refuse(ctx, entity.getMvumoRecordId(), payload);
        entity.setStatus(ConsentStatus.REFUSED);
        entity = mvumoRepository.save(entity);
        BookingEntity booking = requireBooking(bookingId, ctx.tenantId());
        booking.setConsentStatus(ConsentStatus.REFUSED);
        bookingRepository.save(booking);
        return entity;
    }

    @Transactional
    public BookingMvumoEntity revoke(UUID bookingId, MvumoType type) {
        TrustContext ctx = TrustContextHolder.require();
        BookingMvumoEntity entity = requireMvumo(bookingId, type);
        entity.setStatus(ConsentStatus.REVOKED);
        entity = mvumoRepository.save(entity);
        BookingEntity booking = requireBooking(bookingId, ctx.tenantId());
        booking.setConsentStatus(ConsentStatus.REVOKED);
        bookingRepository.save(booking);
        return entity;
    }

    @Transactional(readOnly = true)
    public ConsentStatus status(UUID bookingId, MvumoType type) {
        return mvumoRepository.findByBookingIdAndMvumoType(bookingId, type)
                .map(BookingMvumoEntity::getStatus)
                .orElse(ConsentStatus.NOT_REQUIRED);
    }

    public void assertConsentGrantedForConfirmation(BookingEntity booking) {
        if (booking.getConsentStatus() == ConsentStatus.NOT_REQUIRED) {
            return;
        }
        if (booking.getConsentStatus() != ConsentStatus.GRANTED) {
            throw new IllegalStateException("Mvumo consent must be GRANTED before confirmation");
        }
    }

    private void syncBookingConsent(UUID bookingId, UUID tenantId) {
        BookingEntity booking = requireBooking(bookingId, tenantId);
        boolean allGranted = mvumoRepository.findByBookingIdAndTenantId(bookingId, tenantId).stream()
                .allMatch(m -> m.getStatus() == ConsentStatus.GRANTED || m.getStatus() == ConsentStatus.NOT_REQUIRED);
        if (allGranted) {
            booking.setConsentStatus(ConsentStatus.GRANTED);
            bookingRepository.save(booking);
        }
    }

    private BookingEntity requireBooking(UUID bookingId, UUID tenantId) {
        return bookingRepository.findByIdAndTenantId(bookingId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingId));
    }

    private BookingMvumoEntity requireMvumo(UUID bookingId, MvumoType type) {
        return mvumoRepository.findByBookingIdAndMvumoType(bookingId, type)
                .orElseThrow(() -> new IllegalArgumentException("Mvumo record not found for booking " + bookingId));
    }

    private static String stringOrDefault(Object value, String fallback) {
        return value != null ? value.toString() : fallback;
    }
}
