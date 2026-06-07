package zw.gov.mohcc.impilo.booking.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.booking.domain.*;
import zw.gov.mohcc.impilo.booking.integration.*;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingEntity;
import zw.gov.mohcc.impilo.booking.persistence.entity.BookingLinkEntity;
import zw.gov.mohcc.impilo.booking.persistence.repository.BookingLinkRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingIntegrationService {

    private final VarapiClient varapiClient;
    private final TusoClient tusoClient;
    private final CostaClient costaClient;
    private final MushexClient mushexClient;
    private final CoverageClient coverageClient;
    private final OrosClient orosClient;
    private final NhumeClient nhumeClient;
    private final LearningClient learningClient;
    private final CommunityClient communityClient;
    private final BookingLinkRepository linkRepository;

    public BookingIntegrationService(VarapiClient varapiClient,
                                     TusoClient tusoClient,
                                     CostaClient costaClient,
                                     MushexClient mushexClient,
                                     CoverageClient coverageClient,
                                     OrosClient orosClient,
                                     NhumeClient nhumeClient,
                                     LearningClient learningClient,
                                     CommunityClient communityClient,
                                     BookingLinkRepository linkRepository) {
        this.varapiClient = varapiClient;
        this.tusoClient = tusoClient;
        this.costaClient = costaClient;
        this.mushexClient = mushexClient;
        this.coverageClient = coverageClient;
        this.orosClient = orosClient;
        this.nhumeClient = nhumeClient;
        this.learningClient = learningClient;
        this.communityClient = communityClient;
        this.linkRepository = linkRepository;
    }

    public List<Map<String, Object>> checkAvailability(TrustContext ctx, Long facilityId, String date, String resourceType) {
        return tusoClient.getAvailableSlots(ctx, facilityId, date, resourceType);
    }

    public EligibilityStatus checkEligibility(TrustContext ctx, BookingEntity booking) {
        if (booking.getProviderId() == null || booking.getProviderId().isBlank()) {
            return EligibilityStatus.UNKNOWN;
        }
        Map<String, Object> result = varapiClient.checkEligibility(ctx, booking.getProviderId(), booking.getFacilityId());
        Object eligible = result.get("eligible");
        if (Boolean.TRUE.equals(eligible)) {
            return EligibilityStatus.ELIGIBLE;
        }
        return EligibilityStatus.INELIGIBLE;
    }

    public Map<String, Object> calculatePayment(TrustContext ctx, BookingEntity booking) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookingId", booking.getId().toString());
        payload.put("bookingNumber", booking.getBookingNumber());
        payload.put("clientId", booking.getClientId());
        payload.put("serviceId", booking.getServiceId());
        payload.put("facilityId", booking.getFacilityId());
        Map<String, Object> bill = costaClient.calculateBill(ctx, payload);
        Map<String, Object> coverage = coverageClient.checkCoverage(ctx, payload);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("bill", bill);
        out.put("coverage", coverage);
        if (Boolean.TRUE.equals(coverage.get("covered"))) {
            out.put("paymentStatus", PaymentStatus.EXEMPTED.name());
        } else if (!bill.isEmpty()) {
            out.put("paymentIntent", mushexClient.createPaymentIntent(ctx, payload));
            out.put("paymentStatus", PaymentStatus.PENDING.name());
        } else {
            out.put("paymentStatus", PaymentStatus.NOT_REQUIRED.name());
        }
        return out;
    }

    public UUID reserveResource(TrustContext ctx, BookingEntity booking) {
        if (booking.getResourceId() == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bookingNumber", booking.getBookingNumber());
        payload.put("clientId", booking.getClientId());
        if (booking.getPreferredStartAt() != null) {
            payload.put("startAt", booking.getPreferredStartAt().toString());
        }
        if (booking.getPreferredEndAt() != null) {
            payload.put("endAt", booking.getPreferredEndAt().toString());
        }
        Map<String, Object> response = tusoClient.createBooking(ctx, booking.getResourceId(), payload);
        Object id = response.get("id");
        if (id != null) {
            UUID tusoBookingId = UUID.fromString(id.toString());
            persistLink(ctx, booking.getId(), LinkType.TUSO_RESOURCE, tusoBookingId.toString());
            return tusoBookingId;
        }
        return null;
    }

    public void createTypeSpecificLinks(TrustContext ctx, BookingEntity booking) {
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("bookingId", booking.getId().toString());
        base.put("bookingNumber", booking.getBookingNumber());
        base.put("clientId", booking.getClientId());
        base.put("facilityId", booking.getFacilityId());

        switch (booking.getBookingType()) {
            case LAB, RADIOLOGY -> linkFromResponse(ctx, booking.getId(), LinkType.ORDER,
                    orosClient.createLabOrderLink(ctx, base));
            case BED -> linkFromResponse(ctx, booking.getId(), LinkType.INPATIENT_BED,
                    Map.of("type", "INPATIENT_BED", "bookingId", booking.getId().toString()));
            case DISPATCH_DELIVERY -> linkFromResponse(ctx, booking.getId(), LinkType.DISPATCH,
                    nhumeClient.createDispatchLink(ctx, base));
            case TRAINING -> linkFromResponse(ctx, booking.getId(), LinkType.TRAINING_SESSION,
                    learningClient.createTrainingSessionLink(ctx, base));
            case OUTREACH -> linkFromResponse(ctx, booking.getId(), LinkType.OUTREACH_EVENT,
                    communityClient.createOutreachLink(ctx, base));
            default -> { /* no type-specific link */ }
        }
    }

    public Map<String, Object> resolveFacility(TrustContext ctx, Long facilityId) {
        return tusoClient.getFacility(ctx, facilityId);
    }

    public String formatPreferredDate(BookingEntity booking) {
        if (booking.getPreferredStartAt() == null) {
            return DateTimeFormatter.ISO_LOCAL_DATE.format(java.time.LocalDate.now());
        }
        return DateTimeFormatter.ISO_LOCAL_DATE.format(
                booking.getPreferredStartAt().atZone(java.time.ZoneOffset.UTC).toLocalDate());
    }

    private void linkFromResponse(TrustContext ctx, UUID bookingId, LinkType type, Map<String, Object> response) {
        Object linkId = response.get("id");
        if (linkId == null) {
            linkId = response.get("linkId");
        }
        if (linkId != null) {
            persistLink(ctx, bookingId, type, linkId.toString());
        }
    }

    private void persistLink(TrustContext ctx, UUID bookingId, LinkType type, String linkId) {
        BookingLinkEntity link = new BookingLinkEntity();
        link.setTenantId(ctx.tenantId());
        link.setBookingId(bookingId);
        link.setLinkType(type);
        link.setLinkId(linkId);
        linkRepository.save(link);
    }
}
