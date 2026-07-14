package zw.gov.mohcc.impilo.scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.scheduling.core.ConflictDetectionService;
import zw.gov.mohcc.impilo.scheduling.integration.TusoResourceClient;
import zw.gov.mohcc.impilo.scheduling.integration.VashandiTeamClient;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.OrListItemEntity;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.ResourceReservationEntity;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.TheatreSessionEntity;
import zw.gov.mohcc.impilo.scheduling.persistence.repository.ResourceReservationRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Conflict detection runs against REAL availability data (held resource
 * reservations of other published sessions). Verifies that an overlapping
 * surgeon reservation blocks publish.
 */
class ConflictDetectionServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final LocalDate DATE = LocalDate.of(2026, 3, 2);

    private ResourceReservationRepository reservationRepository;
    private TusoResourceClient tuso;
    private VashandiTeamClient vashandi;
    private ConflictDetectionService service;

    @BeforeEach
    void setUp() {
        reservationRepository = mock(ResourceReservationRepository.class);
        tuso = mock(TusoResourceClient.class);
        vashandi = mock(VashandiTeamClient.class);
        service = new ConflictDetectionService(reservationRepository, tuso, vashandi);
        when(tuso.clinicalSpaceAvailability(any(), any())).thenReturn(TusoResourceClient.Availability.UNKNOWN);
        when(vashandi.teamState(any())).thenReturn(VashandiTeamClient.TeamState.UNKNOWN);
        // default: no existing reservations for any resource
        when(reservationRepository.findByTenantIdAndResourceKindAndResourceRefAndReservationDateAndStatus(
                any(), any(), any(), any(), any())).thenReturn(List.of());
    }

    private TheatreSessionEntity session() {
        TheatreSessionEntity s = new TheatreSessionEntity(UUID.randomUUID(), TENANT, "FAC-1", DATE);
        s.setStartTime(LocalTime.of(8, 0));
        s.setTurnoverMinutes(30);
        s.setLeadSurgeonRef("S1");
        return s;
    }

    private OrListItemEntity item(TheatreSessionEntity s, String patient, int durationMin) {
        OrListItemEntity i = new OrListItemEntity(UUID.randomUUID(), TENANT, s.getId(), patient);
        i.setEstimatedDurationMin(durationMin);
        i.setSurgeonRef("S1");
        return i;
    }

    @Test
    void clearWhenNoOverlappingReservations() {
        TheatreSessionEntity s = session();
        ConflictDetectionService.ConflictReport report = service.detect(s, List.of(item(s, "P-1", 60)));
        assertThat(report.clear()).isTrue();
        assertThat(report.blockers()).isEmpty();
    }

    @Test
    void blocksDoubleBookedSurgeonFromRealReservation() {
        TheatreSessionEntity s = session();
        OrListItemEntity item = item(s, "P-1", 60); // window 08:00-09:00

        ResourceReservationEntity heldElsewhere = new ResourceReservationEntity(
                UUID.randomUUID(), TENANT, UUID.randomUUID(), UUID.randomUUID(),
                ResourceReservationEntity.KIND_PRACTITIONER, "S1", DATE,
                LocalTime.of(8, 30), LocalTime.of(9, 30)); // overlaps 08:00-09:00

        when(reservationRepository.findByTenantIdAndResourceKindAndResourceRefAndReservationDateAndStatus(
                eq(TENANT), eq(ResourceReservationEntity.KIND_PRACTITIONER), eq("S1"), eq(DATE),
                eq(ResourceReservationEntity.STATUS_HELD))).thenReturn(List.of(heldElsewhere));

        ConflictDetectionService.ConflictReport report = service.detect(s, List.of(item));

        assertThat(report.clear()).isFalse();
        assertThat(report.blockers())
                .anyMatch(b -> "SURGEON_DOUBLE_BOOKED".equals(b.get("code")));
    }

    @Test
    void tusoUnavailableRoomBlocks() {
        TheatreSessionEntity s = session();
        s.setClinicalSpaceId(UUID.randomUUID());
        when(tuso.clinicalSpaceAvailability(any(), any())).thenReturn(TusoResourceClient.Availability.UNAVAILABLE);

        ConflictDetectionService.ConflictReport report = service.detect(s, List.of(item(s, "P-1", 60)));

        assertThat(report.blockers()).anyMatch(b -> "ROOM_UNAVAILABLE".equals(b.get("code")));
    }

    @Test
    void teamNotConfirmedIsWarningNotBlocker() {
        TheatreSessionEntity s = session();
        s.setVashandiTeamId(UUID.randomUUID());
        when(vashandi.teamState(any())).thenReturn(VashandiTeamClient.TeamState.NOT_CONFIRMED);

        ConflictDetectionService.ConflictReport report = service.detect(s, List.of(item(s, "P-1", 60)));

        assertThat(report.clear()).isTrue();
        assertThat(report.warnings()).anyMatch(w -> "TEAM_NOT_CONFIRMED".equals(w.get("code")));
    }
}
