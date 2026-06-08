package zw.gov.mohcc.impilo.booking.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.booking.api.dto.AppointmentResponse;
import zw.gov.mohcc.impilo.booking.api.dto.BookingResponse;
import zw.gov.mohcc.impilo.booking.domain.*;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingEntity;
import zw.gov.mohcc.impilo.booking.persistence.repository.BookingRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final BookingStateMachine stateMachine;
    private final BookingOutboxPublisher outboxPublisher;
    private final ProviderResolutionService providerResolutionService;
    private final BookingIntegrationService integrationService;
    private final BookingMvumoService mvumoService;
    private final AppointmentService appointmentService;

    public BookingService(BookingRepository bookingRepository,
                          BookingStateMachine stateMachine,
                          BookingOutboxPublisher outboxPublisher,
                          ProviderResolutionService providerResolutionService,
                          BookingIntegrationService integrationService,
                          BookingMvumoService mvumoService,
                          AppointmentService appointmentService) {
        this.bookingRepository = bookingRepository;
        this.stateMachine = stateMachine;
        this.outboxPublisher = outboxPublisher;
        this.providerResolutionService = providerResolutionService;
        this.integrationService = integrationService;
        this.mvumoService = mvumoService;
        this.appointmentService = appointmentService;
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> list(String clientId, String providerId, Long facilityId,
                                      BookingStatus status, int page, int size) {
        TrustContext ctx = TrustContextHolder.require();
        return bookingRepository.search(
                ctx.tenantId(),
                blankToNull(clientId),
                blankToNull(providerId),
                facilityId,
                status,
                PageRequest.of(page, Math.min(size, 100)))
                .getContent().stream().map(BookingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        return BookingResponse.from(requireBooking(id, ctx.tenantId()));
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        BookingEntity booking = new BookingEntity();
        booking.setTenantId(ctx.tenantId());
        booking.setBookingNumber(BookingNumberGenerator.nextBookingNumber());
        booking.setClientId(request.clientId());
        booking.setRequestedByActorId(ctx.actorId());
        booking.setRequestedByActorType(parseActorType(request.requestedByActorType(), ctx.actorType()));
        booking.setBookingType(request.bookingType());
        booking.setServiceId(request.serviceId());
        booking.setServiceName(request.serviceName());
        booking.setFacilityId(request.facilityId());
        booking.setTargetType(request.targetType());
        booking.setTargetRef(request.targetRef());
        booking.setPreferredStartAt(request.preferredStartAt());
        booking.setPreferredEndAt(request.preferredEndAt());
        booking.setPreferredChannel(request.preferredChannel());
        booking.setLocation(request.location());
        booking.setReason(request.reason());
        booking.setClinicalPriority(request.clinicalPriority() != null ? request.clinicalPriority() : ClinicalPriority.ROUTINE);
        booking.setResourceId(request.resourceId());
        booking.setNotes(request.notes());
        booking.setCreatedBy(ctx.actorId());
        booking.setBookingStatus(BookingStatus.REQUESTED);

        if (request.providerId() != null && !request.providerId().isBlank()) {
            ProviderResolutionService.ResolvedProvider provider =
                    providerResolutionService.resolve(ctx, request.providerId());
            booking.setProviderId(provider.providerPublicId());
            booking.setProviderName(provider.providerName());
        }

        if (request.requiresTriage()) {
            booking.setTriageStatus(TriageStatus.PENDING);
        }
        if (request.requiresConsent()) {
            booking.setConsentStatus(ConsentStatus.PENDING);
        }
        if (request.requiresApproval()) {
            booking.setApprovalStatus(ApprovalStatus.PENDING);
            booking.setBookingStatus(BookingStatus.PENDING_APPROVAL);
        }

        booking.setEligibilityStatus(integrationService.checkEligibility(ctx, booking));
        booking = bookingRepository.save(booking);
        integrationService.createTypeSpecificLinks(ctx, booking);
        publishCreated(booking);
        log.info("Booking {} created for client {}", booking.getBookingNumber(), booking.getClientId());
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse update(UUID id, UpdateBookingRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        BookingEntity booking = requireBooking(id, ctx.tenantId());
        if (request.preferredStartAt() != null) {
            booking.setPreferredStartAt(request.preferredStartAt());
        }
        if (request.preferredEndAt() != null) {
            booking.setPreferredEndAt(request.preferredEndAt());
        }
        if (request.reason() != null) {
            booking.setReason(request.reason());
        }
        if (request.notes() != null) {
            booking.setNotes(request.notes());
        }
        booking.setUpdatedBy(ctx.actorId());
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse cancel(UUID id, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        BookingEntity booking = requireBooking(id, ctx.tenantId());
        stateMachine.transition(ctx, booking, BookingStatus.CANCELLED, "CANCEL");
        publishEvent(booking, "booking.cancelled", Map.of("reason", reason != null ? reason : "Cancelled"));
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse triage(UUID id, TriageStatus triageStatus) {
        TrustContext ctx = TrustContextHolder.require();
        BookingEntity booking = requireBooking(id, ctx.tenantId());
        booking.setTriageStatus(triageStatus);
        if (triageStatus == TriageStatus.COMPLETED) {
            stateMachine.transition(ctx, booking, BookingStatus.TRIAGED, "TRIAGE");
        }
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse approve(UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        BookingEntity booking = requireBooking(id, ctx.tenantId());
        booking.setApprovalStatus(ApprovalStatus.APPROVED);
        stateMachine.transition(ctx, booking, BookingStatus.APPROVED, "APPROVE");
        booking = bookingRepository.save(booking);

        if (booking.getConsentStatus() == ConsentStatus.PENDING) {
            mvumoService.requestConsent(booking.getId(), MvumoType.CONSENT);
        }

        AppointmentResponse appointment = appointmentService.createFromBooking(booking);
        booking.setLinkedAppointmentId(appointment.id());
        stateMachine.transition(ctx, booking, BookingStatus.CONFIRMED, "CONFIRM_FROM_APPROVE");
        booking = bookingRepository.save(booking);
        publishEvent(booking, "booking.confirmed", Map.of("appointmentId", appointment.id().toString()));
        return BookingResponse.from(booking);
    }

    @Transactional
    public BookingResponse reject(UUID id, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        BookingEntity booking = requireBooking(id, ctx.tenantId());
        booking.setApprovalStatus(ApprovalStatus.REJECTED);
        stateMachine.transition(ctx, booking, BookingStatus.REJECTED, "REJECT");
        publishEvent(booking, "booking.rejected", Map.of("reason", reason != null ? reason : "Rejected"));
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse assign(UUID id, String providerId, String providerName) {
        TrustContext ctx = TrustContextHolder.require();
        BookingEntity booking = requireBooking(id, ctx.tenantId());
        if (providerId != null && !providerId.isBlank()) {
            ProviderResolutionService.ResolvedProvider provider =
                    providerResolutionService.resolve(ctx, providerId);
            booking.setProviderId(provider.providerPublicId());
            booking.setProviderName(provider.providerName() != null ? provider.providerName() : providerName);
        } else if (providerName != null) {
            booking.setProviderName(providerName);
        }
        booking.setUpdatedBy(ctx.actorId());
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse redirect(UUID id, Long facilityId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        BookingEntity booking = requireBooking(id, ctx.tenantId());
        booking.setFacilityId(facilityId);
        booking.setReason(reason);
        stateMachine.transition(ctx, booking, BookingStatus.REDIRECTED, "REDIRECT");
        stateMachine.transition(ctx, booking, BookingStatus.REQUESTED, "REOPEN");
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public BookingResponse reserve(UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        BookingEntity booking = requireBooking(id, ctx.tenantId());
        UUID tusoBookingId = integrationService.reserveResource(ctx, booking);
        if (tusoBookingId != null) {
            booking.setTusoBookingId(tusoBookingId);
        }
        stateMachine.transition(ctx, booking, BookingStatus.RESERVED, "RESERVE");
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional
    public AppointmentResponse convertToAppointment(UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        BookingEntity booking = requireBooking(id, ctx.tenantId());
        mvumoService.assertConsentGrantedForConfirmation(booking);
        AppointmentResponse appointment = appointmentService.createFromBooking(booking);
        booking.setLinkedAppointmentId(appointment.id());
        stateMachine.transition(ctx, booking, BookingStatus.CONFIRMED, "CONVERT");
        bookingRepository.save(booking);
        publishEvent(booking, "booking.confirmed", Map.of("appointmentId", appointment.id().toString()));
        return appointment;
    }

    @Transactional
    public BookingResponse confirm(UUID id) {
        TrustContext ctx = TrustContextHolder.require();
        BookingEntity booking = requireBooking(id, ctx.tenantId());
        mvumoService.assertConsentGrantedForConfirmation(booking);
        stateMachine.transition(ctx, booking, BookingStatus.CONFIRMED, "CONFIRM");
        publishEvent(booking, "booking.confirmed", Map.of());
        return BookingResponse.from(bookingRepository.save(booking));
    }

    private BookingEntity requireBooking(UUID id, UUID tenantId) {
        return bookingRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + id));
    }

    private void publishCreated(BookingEntity booking) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookingId", booking.getId().toString());
        payload.put("bookingNumber", booking.getBookingNumber());
        payload.put("clientId", booking.getClientId());
        payload.put("status", booking.getBookingStatus().name());
        publishEvent(booking, "booking.created", payload);
    }

    private void publishEvent(BookingEntity booking, String eventType, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookingId", booking.getId().toString());
        payload.put("bookingNumber", booking.getBookingNumber());
        payload.put("status", booking.getBookingStatus().name());
        payload.putAll(extra);
        outboxPublisher.append("BOOKING", booking.getId().toString(), eventType, payload);
    }

    private static ActorType parseActorType(String raw, String ctxActorType) {
        if (raw != null && !raw.isBlank()) {
            return ActorType.valueOf(raw.trim().toUpperCase());
        }
        if (ctxActorType != null && !ctxActorType.isBlank()) {
            try {
                return ActorType.valueOf(ctxActorType.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return ActorType.CITIZEN;
            }
        }
        return ActorType.CITIZEN;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record CreateBookingRequest(
            String clientId,
            String requestedByActorType,
            BookingType bookingType,
            String serviceId,
            String serviceName,
            Long facilityId,
            String providerId,
            TargetType targetType,
            String targetRef,
            Instant preferredStartAt,
            Instant preferredEndAt,
            PreferredChannel preferredChannel,
            String location,
            String reason,
            ClinicalPriority clinicalPriority,
            UUID resourceId,
            String notes,
            boolean requiresTriage,
            boolean requiresConsent,
            boolean requiresApproval
    ) {}

    public record UpdateBookingRequest(
            Instant preferredStartAt,
            Instant preferredEndAt,
            String reason,
            String notes
    ) {}
}
