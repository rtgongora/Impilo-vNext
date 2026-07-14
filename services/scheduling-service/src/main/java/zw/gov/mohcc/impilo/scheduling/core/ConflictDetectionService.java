package zw.gov.mohcc.impilo.scheduling.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.scheduling.integration.TusoResourceClient;
import zw.gov.mohcc.impilo.scheduling.integration.VashandiTeamClient;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.OrListItemEntity;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.ResourceReservationEntity;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.TheatreSessionEntity;
import zw.gov.mohcc.impilo.scheduling.persistence.repository.ResourceReservationRepository;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects theatre scheduling conflicts against REAL availability before publish:
 *  - PRACTITIONER / PATIENT / ROOM double-booking, from held resource_reservation
 *    rows of other already-published sessions (the DB is the authoritative signal);
 *  - within-session overlaps for the same surgeon/patient;
 *  - TUSO clinical-space availability (hard block when explicitly UNAVAILABLE);
 *  - VASHANDI team confirmation (soft warning when not confirmed).
 *
 * Mirrors TheatreService.confirmBooking: blockers refuse publish (409) unless an
 * explicit override + reason is supplied.
 */
@Service
public class ConflictDetectionService {

    private final ResourceReservationRepository reservationRepository;
    private final TusoResourceClient tusoResourceClient;
    private final VashandiTeamClient vashandiTeamClient;

    public ConflictDetectionService(ResourceReservationRepository reservationRepository,
                                    TusoResourceClient tusoResourceClient,
                                    VashandiTeamClient vashandiTeamClient) {
        this.reservationRepository = reservationRepository;
        this.tusoResourceClient = tusoResourceClient;
        this.vashandiTeamClient = vashandiTeamClient;
    }

    public ConflictReport detect(TheatreSessionEntity session, List<OrListItemEntity> items) {
        List<Map<String, String>> blockers = new ArrayList<>();
        List<Window> windows = computeWindows(session, items);
        String roomRef = session.getClinicalSpaceId() != null
                ? session.getClinicalSpaceId().toString() : session.getFacilityId();

        for (int i = 0; i < items.size(); i++) {
            OrListItemEntity item = items.get(i);
            Window w = windows.get(i);
            String surgeon = item.getSurgeonRef() != null ? item.getSurgeonRef() : session.getLeadSurgeonRef();

            // Cross-session double-booking against held reservations elsewhere.
            checkExisting(blockers, session, ResourceReservationEntity.KIND_PRACTITIONER, surgeon, w,
                    "SURGEON_DOUBLE_BOOKED", "Surgeon " + surgeon + " already booked in an overlapping slot");
            checkExisting(blockers, session, ResourceReservationEntity.KIND_PATIENT, item.getPatientId(), w,
                    "PATIENT_DOUBLE_BOOKED", "Patient already booked in an overlapping slot");
            checkExisting(blockers, session, ResourceReservationEntity.KIND_ROOM, roomRef, w,
                    "ROOM_DOUBLE_BOOKED", "Theatre room already booked in an overlapping slot");

            // Within-session overlaps (same surgeon or patient scheduled to overlap).
            for (int j = i + 1; j < items.size(); j++) {
                OrListItemEntity other = items.get(j);
                if (!w.overlaps(windows.get(j))) {
                    continue;
                }
                String otherSurgeon = other.getSurgeonRef() != null
                        ? other.getSurgeonRef() : session.getLeadSurgeonRef();
                if (surgeon != null && surgeon.equals(otherSurgeon)) {
                    blockers.add(blocker("SURGEON_SELF_OVERLAP",
                            "Surgeon " + surgeon + " is on two overlapping cases in this list"));
                }
                if (item.getPatientId().equals(other.getPatientId())) {
                    blockers.add(blocker("PATIENT_SELF_OVERLAP",
                            "Patient appears twice with overlapping slots in this list"));
                }
            }
        }

        // TUSO room availability (hard block only when explicitly UNAVAILABLE).
        String date = session.getSessionDate() != null ? session.getSessionDate().toString() : "";
        TusoResourceClient.Availability room =
                tusoResourceClient.clinicalSpaceAvailability(session.getClinicalSpaceId(), date);
        if (room == TusoResourceClient.Availability.UNAVAILABLE) {
            blockers.add(blocker("ROOM_UNAVAILABLE", "TUSO reports the theatre space is unavailable on " + date));
        }

        // VASHANDI team confirmation (soft warning only — the board enforces TEAM).
        List<Map<String, String>> warnings = new ArrayList<>();
        VashandiTeamClient.TeamState team = vashandiTeamClient.teamState(session.getVashandiTeamId());
        if (team == VashandiTeamClient.TeamState.NOT_CONFIRMED) {
            warnings.add(blocker("TEAM_NOT_CONFIRMED", "Case team is not yet CONFIRMED in VASHANDI"));
        }

        return new ConflictReport(blockers, warnings);
    }

    private void checkExisting(List<Map<String, String>> blockers, TheatreSessionEntity session,
                               String kind, String ref, Window window, String code, String message) {
        if (ref == null || ref.isBlank()) {
            return;
        }
        List<ResourceReservationEntity> held = reservationRepository
                .findByTenantIdAndResourceKindAndResourceRefAndReservationDateAndStatus(
                        session.getTenantId(), kind, ref, session.getSessionDate(),
                        ResourceReservationEntity.STATUS_HELD);
        for (ResourceReservationEntity r : held) {
            Window existing = new Window(r.getStartTime(), r.getEndTime());
            if (window.overlaps(existing)) {
                blockers.add(blocker(code, message));
                return;
            }
        }
    }

    List<Window> computeWindows(TheatreSessionEntity session, List<OrListItemEntity> items) {
        List<Window> windows = new ArrayList<>();
        LocalTime cursor = session.getStartTime() != null ? session.getStartTime() : LocalTime.of(8, 0);
        int turnover = session.getTurnoverMinutes();
        for (OrListItemEntity item : items) {
            LocalTime start = cursor;
            LocalTime end = start.plusMinutes(item.getEstimatedDurationMin());
            windows.add(new Window(start, end));
            cursor = end.plusMinutes(turnover);
        }
        return windows;
    }

    private static Map<String, String> blocker(String code, String message) {
        Map<String, String> b = new LinkedHashMap<>();
        b.put("code", code);
        b.put("message", message);
        return b;
    }

    /** Half-open time window [start, end). */
    public record Window(LocalTime start, LocalTime end) {
        public boolean overlaps(Window other) {
            return start.isBefore(other.end) && other.start.isBefore(end);
        }
    }

    public record ConflictReport(List<Map<String, String>> blockers, List<Map<String, String>> warnings) {
        public boolean clear() {
            return blockers.isEmpty();
        }
    }
}
