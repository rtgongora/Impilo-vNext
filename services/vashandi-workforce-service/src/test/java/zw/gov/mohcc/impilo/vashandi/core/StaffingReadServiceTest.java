package zw.gov.mohcc.impilo.vashandi.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.ShiftEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.ShiftSwapRequestEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.ShiftSwapRequestRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Staffing read-models and the swap workflow (V009).
 *
 * <p>These three surfaces were dead: the BFF asked tuso for {@code /v1/staffing/*}, which no
 * service serves, so the roster screen, on-call screen and control tower rendered empty while
 * every layer reported success. The cases below pin the parts that could go quietly wrong again —
 * a rota that hides a missing second-on-call, and a swap that changes clinical accountability.</p>
 */
@ExtendWith(MockitoExtension.class)
class StaffingReadServiceTest {

    @Mock private ShiftRepository shiftRepository;
    @Mock private ShiftSwapRequestRepository swapRepository;
    @Mock private VashandiOutboxWriter outboxWriter;

    private StaffingReadService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();
    private final LocalDate weekStart = LocalDate.of(2026, 7, 27);

    @BeforeEach
    void setUp() {
        service = new StaffingReadService(shiftRepository, swapRepository, outboxWriter);
    }

    private ShiftEntity shift(String role, String specialty, UUID profileId, int dayOffset) {
        ShiftEntity s = new ShiftEntity();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenantId);
        s.setFacilityId(facilityId);
        s.setWorkforceProfileId(profileId);
        s.setShiftType("NIGHT");
        s.setOnCallRole(role);
        s.setSpecialty(specialty);
        s.setStatus("scheduled");
        s.setStartTime(weekStart.plusDays(dayOffset).atStartOfDay().atOffset(ZoneOffset.UTC).plusHours(18));
        s.setEndTime(weekStart.plusDays(dayOffset + 1).atStartOfDay().atOffset(ZoneOffset.UTC).plusHours(8));
        return s;
    }

    // ── on-call rota ────────────────────────────────────────────────────────

    @Test
    void theRotaPairsFirstCallWithSecondCall() {
        UUID primary = UUID.randomUUID();
        UUID backup = UUID.randomUUID();
        when(shiftRepository.findOnCallForFacilityWindow(any(), any(), any(), any()))
                .thenReturn(List.of(
                        shift(StaffingReadService.ROLE_PRIMARY, "SURGERY", primary, 0),
                        shift(StaffingReadService.ROLE_BACKUP, "SURGERY", backup, 0)));

        List<Map<String, Object>> rota = service.onCallWeek(tenantId, facilityId, weekStart);

        assertThat(rota).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) rota.get(0).get("attributes");
        assertThat(attributes)
                .containsEntry("specialty", "SURGERY")
                .containsEntry("primary_workforce_profile_id", primary.toString())
                .containsEntry("backup_workforce_profile_id", backup.toString());
    }

    /**
     * The single most useful thing this screen can say is "nobody is second on call tonight".
     * Dropping the row would render the night as though it were covered.
     */
    @Test
    void aNightWithNoSecondOnCallIsReportedNotOmitted() {
        when(shiftRepository.findOnCallForFacilityWindow(any(), any(), any(), any()))
                .thenReturn(List.of(shift(StaffingReadService.ROLE_PRIMARY, "SURGERY", UUID.randomUUID(), 0)));

        List<Map<String, Object>> rota = service.onCallWeek(tenantId, facilityId, weekStart);

        assertThat(rota).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) rota.get(0).get("attributes");
        assertThat(attributes.get("primary_workforce_profile_id")).isNotNull();
        assertThat(attributes.get("backup_workforce_profile_id")).isNull();
    }

    @Test
    void separateSpecialtiesOnOneNightAreSeparateRotaRows() {
        when(shiftRepository.findOnCallForFacilityWindow(any(), any(), any(), any()))
                .thenReturn(List.of(
                        shift(StaffingReadService.ROLE_PRIMARY, "SURGERY", UUID.randomUUID(), 0),
                        shift(StaffingReadService.ROLE_PRIMARY, "PAEDIATRICS", UUID.randomUUID(), 0)));

        assertThat(service.onCallWeek(tenantId, facilityId, weekStart)).hasSize(2);
    }

    /** vashandi holds no telephone numbers; null means "not known here", never "none". */
    @Test
    void theRotaNeverInventsAContactNumber() {
        when(shiftRepository.findOnCallForFacilityWindow(any(), any(), any(), any()))
                .thenReturn(List.of(shift(StaffingReadService.ROLE_PRIMARY, "SURGERY", UUID.randomUUID(), 0)));

        @SuppressWarnings("unchecked")
        Map<String, Object> attributes =
                (Map<String, Object>) service.onCallWeek(tenantId, facilityId, weekStart).get(0).get("attributes");
        assertThat(attributes).containsEntry("primary_phone", null).containsEntry("backup_phone", null);
    }

    // ── roster week ─────────────────────────────────────────────────────────

    @Test
    void rosterWeekCoversSevenDaysFromTheWeekStart() {
        when(shiftRepository.findByTenantIdAndFacilityIdAndStartTimeBetweenOrderByStartTimeAsc(
                any(), any(), any(), any())).thenReturn(List.of());

        service.rosterWeek(tenantId, facilityId, weekStart);

        OffsetDateTime from = weekStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        verify(shiftRepository).findByTenantIdAndFacilityIdAndStartTimeBetweenOrderByStartTimeAsc(
                tenantId, facilityId, from, from.plusDays(7));
    }

    // ── swaps ───────────────────────────────────────────────────────────────

    @Test
    void aSwapIsRaisedAgainstAShiftAndCarriesItsOwner() throws Exception {
        UUID other = UUID.randomUUID();
        ShiftEntity s = shift(null, null, UUID.randomUUID(), 0);
        when(shiftRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(swapRepository.findByRequestingShiftIdAndStatus(s.getId(), "PENDING")).thenReturn(Optional.empty());
        when(swapRepository.save(any(ShiftSwapRequestEntity.class))).thenAnswer(inv -> {
            ShiftSwapRequestEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(UUID.randomUUID());
            return e;
        });

        Map<String, Object> row = service.createSwap(tenantId, s.getId(), other, null, "night cover", "actor-1");

        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) row.get("attributes");
        assertThat(attributes)
                .containsEntry("requesting_workforce_profile_id", s.getWorkforceProfileId().toString())
                .containsEntry("requested_workforce_profile_id", other.toString())
                .containsEntry("status", "PENDING");
    }

    /** A queue of competing PENDING swaps resolves to whichever approval lands last — nobody's decision. */
    @Test
    void aSecondOpenSwapForTheSameShiftIsRefused() {
        ShiftEntity s = shift(null, null, UUID.randomUUID(), 0);
        when(shiftRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(swapRepository.findByRequestingShiftIdAndStatus(s.getId(), "PENDING"))
                .thenReturn(Optional.of(new ShiftSwapRequestEntity()));

        assertThatThrownBy(() -> service.createSwap(tenantId, s.getId(), UUID.randomUUID(), null, null, "a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already open");
    }

    @Test
    void swappingWithYourselfIsRefused() {
        ShiftEntity s = shift(null, null, UUID.randomUUID(), 0);
        when(shiftRepository.findById(s.getId())).thenReturn(Optional.of(s));

        assertThatThrownBy(() ->
                service.createSwap(tenantId, s.getId(), s.getWorkforceProfileId(), null, null, "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Approval must move the shift. Without it the rota still shows the person who is no longer
     * coming — an approved swap that changed nothing on the board.
     */
    @Test
    void approvingASwapMovesTheShiftToTheOtherPerson() throws Exception {
        UUID other = UUID.randomUUID();
        ShiftEntity s = shift(null, null, UUID.randomUUID(), 0);

        ShiftSwapRequestEntity swap = new ShiftSwapRequestEntity();
        swap.setId(UUID.randomUUID());
        swap.setTenantId(tenantId);
        swap.setStatus("PENDING");
        swap.setRequestingShiftId(s.getId());
        swap.setRequestingProfileId(s.getWorkforceProfileId());
        swap.setRequestedProfileId(other);

        when(swapRepository.findByTenantIdAndId(tenantId, swap.getId())).thenReturn(Optional.of(swap));
        when(swapRepository.save(any(ShiftSwapRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shiftRepository.findById(s.getId())).thenReturn(Optional.of(s));

        service.decideSwap(tenantId, swap.getId(), "APPROVED", "charge-nurse-1", null);

        assertThat(s.getWorkforceProfileId()).isEqualTo(other);
        assertThat(s.getStatus()).isEqualTo("swapped");
    }

    @Test
    void decliningASwapLeavesTheShiftWhereItWas() throws Exception {
        UUID owner = UUID.randomUUID();
        ShiftEntity s = shift(null, null, owner, 0);

        ShiftSwapRequestEntity swap = new ShiftSwapRequestEntity();
        swap.setId(UUID.randomUUID());
        swap.setTenantId(tenantId);
        swap.setStatus("PENDING");
        swap.setRequestingShiftId(s.getId());
        swap.setRequestingProfileId(owner);
        swap.setRequestedProfileId(UUID.randomUUID());

        when(swapRepository.findByTenantIdAndId(tenantId, swap.getId())).thenReturn(Optional.of(swap));
        when(swapRepository.save(any(ShiftSwapRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.decideSwap(tenantId, swap.getId(), "DECLINED", "charge-nurse-1", null);

        assertThat(s.getWorkforceProfileId()).isEqualTo(owner);
        verify(shiftRepository, never()).save(any());
    }

    /** A swap changes who is clinically accountable for a window; an unattributable decision is refused. */
    @Test
    void aDecisionMustRecordWhoMadeIt() {
        ShiftSwapRequestEntity swap = new ShiftSwapRequestEntity();
        swap.setId(UUID.randomUUID());
        swap.setTenantId(tenantId);
        swap.setStatus("PENDING");
        swap.setRequestingShiftId(UUID.randomUUID());
        when(swapRepository.findByTenantIdAndId(tenantId, swap.getId())).thenReturn(Optional.of(swap));

        assertThatThrownBy(() -> service.decideSwap(tenantId, swap.getId(), "APPROVED", "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("who made it");
    }

    @Test
    void anAlreadyDecidedSwapCannotBeDecidedAgain() {
        ShiftSwapRequestEntity swap = new ShiftSwapRequestEntity();
        swap.setId(UUID.randomUUID());
        swap.setTenantId(tenantId);
        swap.setStatus("APPROVED");
        swap.setRequestingShiftId(UUID.randomUUID());
        when(swapRepository.findByTenantIdAndId(tenantId, swap.getId())).thenReturn(Optional.of(swap));

        assertThatThrownBy(() -> service.decideSwap(tenantId, swap.getId(), "DECLINED", "x", null))
                .isInstanceOf(IllegalStateException.class);
    }
}
