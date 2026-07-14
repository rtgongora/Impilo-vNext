package zw.gov.mohcc.impilo.scheduling;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import zw.gov.mohcc.impilo.scheduling.core.ConflictDetectionService;
import zw.gov.mohcc.impilo.scheduling.core.SchedulingOutboxPublisher;
import zw.gov.mohcc.impilo.scheduling.core.SurgicalWaitlistService;
import zw.gov.mohcc.impilo.scheduling.core.TheatreSchedulerService;
import zw.gov.mohcc.impilo.scheduling.integration.BookingClient;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.OrListItemEntity;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.TheatreSessionEntity;
import zw.gov.mohcc.impilo.scheduling.persistence.repository.OrListItemRepository;
import zw.gov.mohcc.impilo.scheduling.persistence.repository.ResourceReservationRepository;
import zw.gov.mohcc.impilo.scheduling.persistence.repository.SurgicalWaitlistRepository;
import zw.gov.mohcc.impilo.scheduling.persistence.repository.TheatreSessionRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the elective funnel keeps ONE case entry point: publish calls
 * booking-service exactly once per item and stamps the returned episode
 * reference — scheduling never creates a second episode path. Cancel returns
 * the case to the waitlist.
 */
class TheatreSchedulerServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private TheatreSessionRepository sessionRepository;
    private OrListItemRepository itemRepository;
    private ResourceReservationRepository reservationRepository;
    private SurgicalWaitlistRepository waitlistRepository;
    private ConflictDetectionService conflictDetection;
    private BookingClient bookingClient;
    private SurgicalWaitlistService waitlistService;
    private SchedulingOutboxPublisher outboxPublisher;
    private TheatreSchedulerService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(TheatreSessionRepository.class);
        itemRepository = mock(OrListItemRepository.class);
        reservationRepository = mock(ResourceReservationRepository.class);
        waitlistRepository = mock(SurgicalWaitlistRepository.class);
        conflictDetection = mock(ConflictDetectionService.class);
        bookingClient = mock(BookingClient.class);
        waitlistService = mock(SurgicalWaitlistService.class);
        outboxPublisher = mock(SchedulingOutboxPublisher.class);
        service = new TheatreSchedulerService(sessionRepository, itemRepository, reservationRepository,
                waitlistRepository, conflictDetection, bookingClient, waitlistService, outboxPublisher);
        TrustContextHolder.set(new TrustContext(TENANT, "scheduler-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        when(itemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private TheatreSessionEntity draftSession() {
        TheatreSessionEntity s = new TheatreSessionEntity(UUID.randomUUID(), TENANT, "FAC-1", LocalDate.of(2026, 4, 1));
        s.setLeadSurgeonRef("S1");
        return s;
    }

    @Test
    void publishCreatesExactlyOneBookingPerItemAndStampsEpisode() {
        TheatreSessionEntity s = draftSession();
        OrListItemEntity i1 = new OrListItemEntity(UUID.randomUUID(), TENANT, s.getId(), "P-1");
        i1.setSurgeonRef("S1");
        OrListItemEntity i2 = new OrListItemEntity(UUID.randomUUID(), TENANT, s.getId(), "P-2");
        i2.setSurgeonRef("S1");
        i2.setSequencePosition(2);

        when(sessionRepository.findByTenantIdAndId(TENANT, s.getId())).thenReturn(Optional.of(s));
        when(itemRepository.findBySessionIdOrderBySequencePositionAsc(s.getId())).thenReturn(List.of(i1, i2));
        when(conflictDetection.detect(any(), any()))
                .thenReturn(new ConflictDetectionService.ConflictReport(List.of(), List.of()));
        when(bookingClient.createTheatreBooking(any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new BookingClient.BookingResult("bk-1", "ep-1")))
                .thenReturn(Optional.of(new BookingClient.BookingResult("bk-2", "ep-2")));

        Map<String, Object> result = service.publish(s.getId().toString(), Map.of());

        // Exactly one booking per item — no second episode-creation path in scheduling.
        verify(bookingClient, times(2)).createTheatreBooking(any(), any(), any(), any(), any());

        ArgumentCaptor<OrListItemEntity> savedItems = ArgumentCaptor.forClass(OrListItemEntity.class);
        verify(itemRepository, times(2)).save(savedItems.capture());
        assertThat(savedItems.getAllValues())
                .allMatch(it -> it.getProcedureEpisodeRef() != null && it.getBookingRef() != null);
        assertThat(result.get("status")).isEqualTo("PUBLISHED");
    }

    @Test
    void publishBlockedByConflictThrows409() {
        TheatreSessionEntity s = draftSession();
        OrListItemEntity i1 = new OrListItemEntity(UUID.randomUUID(), TENANT, s.getId(), "P-1");
        when(sessionRepository.findByTenantIdAndId(TENANT, s.getId())).thenReturn(Optional.of(s));
        when(itemRepository.findBySessionIdOrderBySequencePositionAsc(s.getId())).thenReturn(List.of(i1));
        when(conflictDetection.detect(any(), any())).thenReturn(new ConflictDetectionService.ConflictReport(
                List.of(Map.of("code", "SURGEON_DOUBLE_BOOKED", "message", "clash")), List.of()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.publish(s.getId().toString(), Map.of()))
                .isInstanceOf(TheatreSchedulerService.ConflictException.class);
        // No booking created when publish is refused.
        verify(bookingClient, times(0)).createTheatreBooking(any(), any(), any(), any(), any());
    }

    @Test
    void cancelItemReturnsCaseToWaitlist() {
        TheatreSessionEntity s = draftSession();
        UUID waitlistEntryId = UUID.randomUUID();
        OrListItemEntity item = new OrListItemEntity(UUID.randomUUID(), TENANT, s.getId(), "P-1");
        item.setWaitlistEntryId(waitlistEntryId);

        when(sessionRepository.findByTenantIdAndId(TENANT, s.getId())).thenReturn(Optional.of(s));
        when(itemRepository.findByTenantIdAndId(TENANT, item.getId())).thenReturn(Optional.of(item));
        when(reservationRepository.findBySessionId(s.getId())).thenReturn(List.of());

        service.cancelItem(s.getId().toString(), item.getId().toString(),
                Map.of("cancelReasonCode", "PATIENT_UNWELL", "reason", "temperature"));

        assertThat(item.getStatus()).isEqualTo("CANCELLED");
        verify(waitlistService).returnFromCancelledItem(eq(TENANT), eq(waitlistEntryId), any());
    }
}
