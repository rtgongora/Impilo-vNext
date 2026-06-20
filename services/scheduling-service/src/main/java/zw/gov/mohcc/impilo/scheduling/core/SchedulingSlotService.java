package zw.gov.mohcc.impilo.scheduling.core;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.CapacityRuleEntity;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.SlotReservationEntity;
import zw.gov.mohcc.impilo.scheduling.persistence.repository.CapacityRuleRepository;
import zw.gov.mohcc.impilo.scheduling.persistence.repository.SlotReservationRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SchedulingSlotService {

    private final SlotReservationRepository reservationRepository;
    private final CapacityRuleRepository capacityRuleRepository;
    private final SchedulingOutboxPublisher outboxPublisher;

    public SchedulingSlotService(
            SlotReservationRepository reservationRepository,
            CapacityRuleRepository capacityRuleRepository,
            SchedulingOutboxPublisher outboxPublisher) {
        this.reservationRepository = reservationRepository;
        this.capacityRuleRepository = capacityRuleRepository;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listSlots(String resourceId, String date) {
        UUID tenantId = tenantId();
        LocalDate slotDate = LocalDate.parse(date);
        Set<LocalTime> reserved = reservedTimes(tenantId, resourceId, slotDate);

        List<Map<String, Object>> out = new ArrayList<>();
        LocalTime t = LocalTime.of(8, 0);
        LocalTime end = LocalTime.of(17, 0);
        while (t.isBefore(end)) {
            out.add(Map.of(
                    "start", slotDate.atTime(t).toString(),
                    "duration_minutes", 20,
                    "available", !reserved.contains(t)));
            t = t.plusMinutes(20);
        }
        return Map.of(
                "resource_id", resourceId,
                "date", slotDate.toString(),
                "tenant_id", tenantId.toString(),
                "slots", out);
    }

    @Transactional
    public Map<String, Object> reserve(String resourceId, String date, String startTime, String holdToken) {
        UUID tenantId = tenantId();
        LocalDate slotDate = LocalDate.parse(date);
        LocalTime start = LocalTime.parse(startTime);
        String token = holdToken != null && !holdToken.isBlank() ? holdToken : UUID.randomUUID().toString();

        var existing = reservationRepository.findByTenantIdAndResourceIdAndSlotDateAndStartTime(
                tenantId, resourceId, slotDate, start);
        if (existing.isPresent() && !existing.get().getHoldToken().equals(token)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "slot_already_reserved");
        }
        if (existing.isEmpty()) {
            try {
                reservationRepository.save(new SlotReservationEntity(tenantId, resourceId, slotDate, start, token));
                emitSlotEvent("SLOT_RESERVED", tenantId, resourceId, slotDate, start, token);
            } catch (DataIntegrityViolationException ex) {
                var raced = reservationRepository.findByTenantIdAndResourceIdAndSlotDateAndStartTime(
                        tenantId, resourceId, slotDate, start);
                if (raced.isPresent() && !raced.get().getHoldToken().equals(token)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "slot_already_reserved");
                }
            }
        }
        return Map.of(
                "status", "RESERVED",
                "hold_token", token,
                "resource_id", resourceId,
                "date", date,
                "start_time", startTime,
                "tenant_id", tenantId.toString());
    }

    @Transactional
    public Map<String, Object> release(String resourceId, String date, String startTime, String holdToken) {
        UUID tenantId = tenantId();
        LocalDate slotDate = LocalDate.parse(date);
        LocalTime start = LocalTime.parse(startTime);
        reservationRepository.findByTenantIdAndResourceIdAndSlotDateAndStartTime(tenantId, resourceId, slotDate, start)
                .filter(row -> row.getHoldToken().equals(holdToken))
                .ifPresent(reservationRepository::delete);
        emitSlotEvent("SLOT_RELEASED", tenantId, resourceId, slotDate, start, holdToken);
        return Map.of(
                "status", "RELEASED",
                "resource_id", resourceId,
                "date", date,
                "start_time", startTime,
                "tenant_id", tenantId.toString());
    }

    @Transactional
    public Map<String, Object> capacityRules() {
        UUID tenantId = tenantId();
        CapacityRuleEntity rules = capacityRuleRepository.findById(tenantId)
                .orElseGet(() -> capacityRuleRepository.save(CapacityRuleEntity.defaults(tenantId)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenant_id", tenantId.toString());
        body.put("version", rules.getVersion());
        body.put("overbooking_factor", rules.getOverbookingFactor());
        body.put("waitlist_enabled", rules.isWaitlistEnabled());
        body.put("holiday_calendar", rules.getHolidayCalendar());
        body.put("notes", rules.getNotes());
        return body;
    }

    private Set<LocalTime> reservedTimes(UUID tenantId, String resourceId, LocalDate slotDate) {
        Set<LocalTime> reserved = new HashSet<>();
        for (SlotReservationEntity row : reservationRepository.findByTenantIdAndResourceIdAndSlotDate(tenantId, resourceId, slotDate)) {
            reserved.add(row.getStartTime());
        }
        return reserved;
    }

    private UUID tenantId() {
        return TrustContextHolder.require().tenantId();
    }

    private void emitSlotEvent(
            String eventType,
            UUID tenantId,
            String resourceId,
            LocalDate slotDate,
            LocalTime startTime,
            String holdToken) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("resourceId", resourceId);
        payload.put("date", slotDate.toString());
        payload.put("startTime", startTime.toString());
        payload.put("holdToken", holdToken);
        outboxPublisher.append("SlotReservation", resourceId + "|" + slotDate + "|" + startTime, eventType, payload);
    }
}
