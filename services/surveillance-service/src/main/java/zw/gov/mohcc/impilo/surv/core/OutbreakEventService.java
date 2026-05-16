package zw.gov.mohcc.impilo.surv.core;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.surv.persistence.entity.OutbreakEventEntity;
import zw.gov.mohcc.impilo.surv.persistence.entity.SignalEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.OutbreakEventRepository;

@Service
public class OutbreakEventService {

    private final OutbreakEventRepository outbreakEventRepository;
    private final SignalService signalService;

    public OutbreakEventService(OutbreakEventRepository outbreakEventRepository, SignalService signalService) {
        this.outbreakEventRepository = outbreakEventRepository;
        this.signalService = signalService;
    }

    @Transactional(readOnly = true)
    public List<OutbreakEventEntity> listOutbreaks(UUID tenantId) {
        return outbreakEventRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public OutbreakEventEntity recordOutbreak(
            UUID tenantId,
            String actorId,
            UUID correlationId,
            String name,
            String description,
            String eventType,
            String conditionField,
            int threshold,
            int windowHours,
            Severity severity,
            String province,
            String district,
            UUID facilityId,
            Double latitude,
            Double longitude) {

        SignalEntity signal = signalService.createSignal(
                tenantId,
                actorId,
                correlationId,
                name,
                description,
                eventType != null && !eventType.isBlank() ? eventType : "OUTBREAK",
                conditionField != null && !conditionField.isBlank() ? conditionField : "syndrome_code",
                threshold > 0 ? threshold : 1,
                windowHours > 0 ? windowHours : 24,
                severity != null ? severity : Severity.HIGH);

        OutbreakEventEntity outbreak = new OutbreakEventEntity();
        outbreak.setTenantId(tenantId);
        outbreak.setSignalId(signal.getId());
        outbreak.setName(name);
        outbreak.setDescription(description);
        outbreak.setEventType(signal.getEventType());
        outbreak.setLifecycleStatus(OutbreakLifecycleStatus.RECORDED);
        outbreak.setSeverity(signal.getSeverity());
        outbreak.setProvince(province);
        outbreak.setDistrict(district);
        outbreak.setFacilityId(facilityId);
        outbreak.setLatitude(latitude);
        outbreak.setLongitude(longitude);
        outbreak.setCreatedBy(actorId);
        return outbreakEventRepository.save(outbreak);
    }

    @Transactional
    public OutbreakEventEntity transition(UUID tenantId, Long outbreakId, OutbreakLifecycleStatus nextStatus) {
        OutbreakEventEntity outbreak = outbreakEventRepository.findById(outbreakId)
                .filter(o -> tenantId.equals(o.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Outbreak not found"));
        validateOutbreakTransition(outbreak.getLifecycleStatus(), nextStatus);
        outbreak.setLifecycleStatus(nextStatus);
        return outbreakEventRepository.save(outbreak);
    }

    private void validateOutbreakTransition(OutbreakLifecycleStatus current, OutbreakLifecycleStatus next) {
        if (current == next) {
            return;
        }
        Map<OutbreakLifecycleStatus, List<OutbreakLifecycleStatus>> allowed = Map.of(
                OutbreakLifecycleStatus.RECORDED, List.of(OutbreakLifecycleStatus.CONFIRMED, OutbreakLifecycleStatus.CLOSED),
                OutbreakLifecycleStatus.CONFIRMED, List.of(OutbreakLifecycleStatus.INVESTIGATING, OutbreakLifecycleStatus.CLOSED),
                OutbreakLifecycleStatus.INVESTIGATING, List.of(OutbreakLifecycleStatus.CONTAINED, OutbreakLifecycleStatus.CLOSED),
                OutbreakLifecycleStatus.CONTAINED, List.of(OutbreakLifecycleStatus.CLOSED),
                OutbreakLifecycleStatus.CLOSED, List.of());
        if (!allowed.getOrDefault(current, List.of()).contains(next)) {
            throw new IllegalArgumentException(
                    "Invalid outbreak transition from " + current + " to " + next);
        }
    }

    public static Severity parseSeverity(String raw) {
        if (raw == null || raw.isBlank()) {
            return Severity.HIGH;
        }
        try {
            return Severity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Severity.HIGH;
        }
    }
}
