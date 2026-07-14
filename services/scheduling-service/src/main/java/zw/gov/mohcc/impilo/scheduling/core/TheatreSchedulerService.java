package zw.gov.mohcc.impilo.scheduling.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.scheduling.integration.BookingClient;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.OrListItemEntity;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.ResourceReservationEntity;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.SurgicalWaitlistEntryEntity;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.TheatreSessionEntity;
import zw.gov.mohcc.impilo.scheduling.persistence.repository.OrListItemRepository;
import zw.gov.mohcc.impilo.scheduling.persistence.repository.ResourceReservationRepository;
import zw.gov.mohcc.impilo.scheduling.persistence.repository.SurgicalWaitlistRepository;
import zw.gov.mohcc.impilo.scheduling.persistence.repository.TheatreSessionRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns OR sessions / lists. The publish path is the elective funnel: for each
 * item it creates a THEATRE booking (booking-service), which runs the EXISTING
 * BookingIntegrationService.ensureProcedureEpisode() to reach the ONE case source
 * of truth (inpatient.procedure_episode). Scheduling stamps only references.
 */
@Service
public class TheatreSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(TheatreSchedulerService.class);

    private final TheatreSessionRepository sessionRepository;
    private final OrListItemRepository itemRepository;
    private final ResourceReservationRepository reservationRepository;
    private final SurgicalWaitlistRepository waitlistRepository;
    private final ConflictDetectionService conflictDetection;
    private final BookingClient bookingClient;
    private final SurgicalWaitlistService waitlistService;
    private final SchedulingOutboxPublisher outboxPublisher;

    public TheatreSchedulerService(TheatreSessionRepository sessionRepository,
                                   OrListItemRepository itemRepository,
                                   ResourceReservationRepository reservationRepository,
                                   SurgicalWaitlistRepository waitlistRepository,
                                   ConflictDetectionService conflictDetection,
                                   BookingClient bookingClient,
                                   SurgicalWaitlistService waitlistService,
                                   SchedulingOutboxPublisher outboxPublisher) {
        this.sessionRepository = sessionRepository;
        this.itemRepository = itemRepository;
        this.reservationRepository = reservationRepository;
        this.waitlistRepository = waitlistRepository;
        this.conflictDetection = conflictDetection;
        this.bookingClient = bookingClient;
        this.waitlistService = waitlistService;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional
    public Map<String, Object> createSession(Map<String, Object> body) {
        UUID tenantId = tenantId();
        String facilityId = str(body, "facilityId");
        String date = str(body, "sessionDate");
        if (facilityId == null || date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "facilityId and sessionDate are required");
        }
        TheatreSessionEntity session =
                new TheatreSessionEntity(UUID.randomUUID(), tenantId, facilityId, LocalDate.parse(date));
        if (str(body, "clinicalSpaceId") != null) {
            session.setClinicalSpaceId(UUID.fromString(str(body, "clinicalSpaceId")));
        }
        if (str(body, "startTime") != null) {
            session.setStartTime(LocalTime.parse(str(body, "startTime")));
        }
        if (str(body, "endTime") != null) {
            session.setEndTime(LocalTime.parse(str(body, "endTime")));
        }
        session.setSlot(str(body, "slot"));
        if (body.get("turnoverMinutes") != null) {
            session.setTurnoverMinutes(intOf(body.get("turnoverMinutes"), 30));
        }
        session.setLeadSurgeonRef(str(body, "leadSurgeonRef"));
        if (str(body, "vashandiTeamId") != null) {
            session.setVashandiTeamId(UUID.fromString(str(body, "vashandiTeamId")));
        }
        sessionRepository.save(session);
        return sessionEnvelope(session);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSessions(String facilityId, String date) {
        UUID tenantId = tenantId();
        List<TheatreSessionEntity> sessions;
        if (date != null && !date.isBlank() && facilityId != null && !facilityId.isBlank()) {
            sessions = sessionRepository.findByTenantIdAndFacilityIdAndSessionDateOrderByStartTimeAsc(
                    tenantId, facilityId, LocalDate.parse(date));
        } else if (date != null && !date.isBlank()) {
            sessions = sessionRepository.findByTenantIdAndSessionDateOrderByStartTimeAsc(
                    tenantId, LocalDate.parse(date));
        } else {
            sessions = sessionRepository.findByTenantIdAndSessionDateOrderByStartTimeAsc(tenantId, LocalDate.now());
        }
        return sessions.stream().map(this::sessionEnvelope).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSession(String sessionId) {
        return sessionEnvelope(requireSession(sessionId));
    }

    @Transactional
    public Map<String, Object> addItem(String sessionId, Map<String, Object> body) {
        TheatreSessionEntity session = requireSession(sessionId);
        if (!TheatreSessionEntity.STATUS_DRAFT.equals(session.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot add items to a " + session.getStatus() + " session");
        }
        String patientId = str(body, "patientId");
        if (patientId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientId is required");
        }
        List<OrListItemEntity> existing = itemRepository.findBySessionIdOrderBySequencePositionAsc(session.getId());
        OrListItemEntity item = new OrListItemEntity(UUID.randomUUID(), session.getTenantId(), session.getId(), patientId);
        item.setZibo(str(body, "zibo"));
        item.setSurgeonRef(str(body, "surgeonRef"));
        item.setEstimatedDurationMin(intOf(body.get("estimatedDurationMin"), 60));
        item.setSequencePosition(existing.size() + 1);
        if (str(body, "waitlistEntryId") != null) {
            item.setWaitlistEntryId(UUID.fromString(str(body, "waitlistEntryId")));
        }
        item.appendHistory("ADDED", actor(), "added to OR list");
        itemRepository.save(item);
        return item.toEnvelope();
    }

    @Transactional
    public Map<String, Object> reorderItems(String sessionId, List<String> orderedItemIds) {
        TheatreSessionEntity session = requireSession(sessionId);
        int position = 1;
        for (String id : orderedItemIds) {
            OrListItemEntity item = itemRepository.findByTenantIdAndId(session.getTenantId(), UUID.fromString(id))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + id));
            item.setSequencePosition(position++);
            item.appendHistory("REORDERED", actor(), "position " + item.getSequencePosition());
            itemRepository.save(item);
        }
        return sessionEnvelope(session);
    }

    /**
     * Publish the session. Refuses (409) on real conflicts unless override+reason.
     * For each item: create a THEATRE booking (funnels to ensureProcedureEpisode),
     * stamp booking_ref + procedure_episode_ref, hold resource reservations, and
     * move the waitlist entry to SCHEDULED.
     */
    @Transactional
    public Map<String, Object> publish(String sessionId, Map<String, Object> body) {
        TheatreSessionEntity session = requireSession(sessionId);
        List<OrListItemEntity> items = itemRepository.findBySessionIdOrderBySequencePositionAsc(session.getId());
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot publish an empty OR list");
        }

        ConflictDetectionService.ConflictReport report = conflictDetection.detect(session, items);
        boolean override = body != null && (Boolean.TRUE.equals(body.get("override"))
                || Boolean.parseBoolean(String.valueOf(body.getOrDefault("override", "false"))));
        if (!report.clear() && !override) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "PUBLISH_BLOCKED");
            err.put("blockers", report.blockers());
            err.put("warnings", report.warnings());
            throw new ConflictException(err);
        }
        if (!report.clear()) {
            String reason = str(body, "overrideReason", "reason");
            if (reason == null || reason.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "overrideReason is required to override publish conflicts");
            }
        }

        List<Window> windows = itemWindows(session, items);
        String roomRef = session.getClinicalSpaceId() != null
                ? session.getClinicalSpaceId().toString() : session.getFacilityId();

        for (int i = 0; i < items.size(); i++) {
            OrListItemEntity item = items.get(i);
            Window w = windows.get(i);

            // Funnel through booking → ensureProcedureEpisode (the ONE case SoR).
            String startAt = session.getSessionDate() + "T" + w.start() + ":00Z";
            BookingClient.BookingResult result = bookingClient.createTheatreBooking(
                    item.getPatientId(), item.getZibo(), item.getSurgeonRef(), session.getClinicalSpaceId(), startAt)
                    .orElse(new BookingClient.BookingResult(null, null));
            item.setBookingRef(result.bookingId());
            if (result.episodeRef() != null) {
                item.setProcedureEpisodeRef(result.episodeRef());
            }
            item.setStatus(OrListItemEntity.STATUS_PUBLISHED);
            item.appendHistory("PUBLISHED", actor(),
                    "booking=" + result.bookingId() + " episode=" + result.episodeRef());
            itemRepository.save(item);

            // Hold resource reservations (the double-book substrate).
            String surgeon = item.getSurgeonRef() != null ? item.getSurgeonRef() : session.getLeadSurgeonRef();
            hold(session, item, ResourceReservationEntity.KIND_ROOM, roomRef, w);
            hold(session, item, ResourceReservationEntity.KIND_PRACTITIONER, surgeon, w);
            hold(session, item, ResourceReservationEntity.KIND_PATIENT, item.getPatientId(), w);

            // Move waitlist entry to SCHEDULED.
            if (item.getWaitlistEntryId() != null) {
                waitlistService.markScheduled(session.getTenantId(), item.getWaitlistEntryId(), item.getId());
            }
            outboxPublisher.append("TheatreSession", session.getId().toString(),
                    "scheduling.theatre.item.scheduled", Map.of(
                            "sessionId", session.getId().toString(),
                            "orListItemId", item.getId().toString(),
                            "patientId", item.getPatientId(),
                            "bookingRef", nullSafe(item.getBookingRef()),
                            "procedureEpisodeRef", nullSafe(item.getProcedureEpisodeRef())));
        }

        session.setStatus(TheatreSessionEntity.STATUS_PUBLISHED);
        sessionRepository.save(session);
        outboxPublisher.append("TheatreSession", session.getId().toString(),
                "scheduling.theatre.session.published", Map.of(
                        "sessionId", session.getId().toString(),
                        "facilityId", session.getFacilityId(),
                        "itemCount", items.size(),
                        "overridden", !report.clear()));
        notifyKhuluma(session, "session_published");
        return sessionEnvelope(session);
    }

    @Transactional
    public Map<String, Object> reschedule(String sessionId, Map<String, Object> body) {
        TheatreSessionEntity session = requireSession(sessionId);
        if (str(body, "sessionDate") != null) {
            session.setSessionDate(LocalDate.parse(str(body, "sessionDate")));
        }
        if (str(body, "startTime") != null) {
            session.setStartTime(LocalTime.parse(str(body, "startTime")));
        }
        sessionRepository.save(session);
        outboxPublisher.append("TheatreSession", session.getId().toString(),
                "scheduling.theatre.session.rescheduled", Map.of(
                        "sessionId", session.getId().toString(),
                        "sessionDate", String.valueOf(session.getSessionDate())));
        notifyKhuluma(session, "session_rescheduled");
        return sessionEnvelope(session);
    }

    /** Cancel an item with a structured reason and return it to the waitlist. */
    @Transactional
    public Map<String, Object> cancelItem(String sessionId, String itemId, Map<String, Object> body) {
        TheatreSessionEntity session = requireSession(sessionId);
        OrListItemEntity item = itemRepository.findByTenantIdAndId(session.getTenantId(), UUID.fromString(itemId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found: " + itemId));
        String code = str(body, "cancelReasonCode", "reasonCode");
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cancelReasonCode is required");
        }
        String text = str(body, "cancelReasonText", "reasonText", "reason");
        item.setStatus(OrListItemEntity.STATUS_CANCELLED);
        item.setCancelReason(code, text);
        item.appendHistory("CANCELLED", actor(), code + ": " + text);
        itemRepository.save(item);

        // Release held reservations for this item.
        for (ResourceReservationEntity r : reservationRepository.findBySessionId(session.getId())) {
            if (r.getStatus().equals(ResourceReservationEntity.STATUS_HELD)) {
                r.release();
                reservationRepository.save(r);
            }
        }
        // Return the case to the waitlist (history preserved on the entry).
        if (item.getWaitlistEntryId() != null) {
            waitlistService.returnFromCancelledItem(session.getTenantId(), item.getWaitlistEntryId(),
                    "OR item cancelled: " + code);
        }
        outboxPublisher.append("TheatreSession", session.getId().toString(),
                "scheduling.theatre.item.cancelled", Map.of(
                        "sessionId", session.getId().toString(),
                        "orListItemId", item.getId().toString(),
                        "cancelReasonCode", code));
        notifyKhuluma(session, "item_cancelled");
        return item.toEnvelope();
    }

    private void hold(TheatreSessionEntity session, OrListItemEntity item, String kind, String ref, Window w) {
        if (ref == null || ref.isBlank()) {
            return;
        }
        ResourceReservationEntity reservation = new ResourceReservationEntity(
                UUID.randomUUID(), session.getTenantId(), session.getId(), item.getId(),
                kind, ref, session.getSessionDate(), w.start(), w.end());
        reservationRepository.save(reservation);
    }

    private List<Window> itemWindows(TheatreSessionEntity session, List<OrListItemEntity> items) {
        List<Window> windows = new ArrayList<>();
        LocalTime cursor = session.getStartTime() != null ? session.getStartTime() : LocalTime.of(8, 0);
        for (OrListItemEntity item : items) {
            LocalTime start = cursor;
            LocalTime end = start.plusMinutes(item.getEstimatedDurationMin());
            windows.add(new Window(start, end));
            cursor = end.plusMinutes(session.getTurnoverMinutes());
        }
        return windows;
    }

    private Map<String, Object> sessionEnvelope(TheatreSessionEntity session) {
        Map<String, Object> out = new LinkedHashMap<>(session.toEnvelope());
        List<Map<String, Object>> items = itemRepository.findBySessionIdOrderBySequencePositionAsc(session.getId())
                .stream().map(OrListItemEntity::toEnvelope).toList();
        out.put("items", items);
        return out;
    }

    private void notifyKhuluma(TheatreSessionEntity session, String kind) {
        // Notification is emitted via the outbox for khuluma to consume; a direct
        // synchronous call is intentionally avoided to keep publish transactional.
        outboxPublisher.append("TheatreNotification", session.getId().toString(),
                "scheduling.theatre.notify", Map.of(
                        "sessionId", session.getId().toString(),
                        "kind", kind,
                        "channel", "khuluma"));
    }

    private TheatreSessionEntity requireSession(String sessionId) {
        UUID id;
        try {
            id = UUID.fromString(sessionId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid session id");
        }
        return sessionRepository.findByTenantIdAndId(tenantId(), id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found: " + sessionId));
    }

    private UUID tenantId() {
        return TrustContextHolder.require().tenantId();
    }

    private String actor() {
        try {
            String a = TrustContextHolder.require().actorId();
            return a != null ? a : "system";
        } catch (RuntimeException ex) {
            return "system";
        }
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

    private static String str(Map<String, Object> body, String... keys) {
        if (body == null) {
            return null;
        }
        for (String k : keys) {
            Object v = body.get(k);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return null;
    }

    private static int intOf(Object v, int def) {
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private record Window(LocalTime start, LocalTime end) {
    }

    /** Publish blocked by conflicts → mapped to 409 by the controller. */
    public static class ConflictException extends RuntimeException {
        private final transient Map<String, Object> detail;

        public ConflictException(Map<String, Object> detail) {
            super("PUBLISH_BLOCKED");
            this.detail = detail;
        }

        public Map<String, Object> detail() {
            return detail;
        }
    }
}
