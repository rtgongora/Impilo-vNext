package zw.gov.mohcc.impilo.surv.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.surv.persistence.entity.FieldTaskEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.FieldTaskRepository;

@Service
public class FieldTaskService {

    private final FieldTaskRepository fieldTaskRepository;
    private final ObjectMapper objectMapper;

    public FieldTaskService(FieldTaskRepository fieldTaskRepository, ObjectMapper objectMapper) {
        this.fieldTaskRepository = fieldTaskRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<FieldTaskEntity> listTasks(UUID tenantId) {
        return fieldTaskRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public FieldTaskEntity createTask(
            UUID tenantId,
            String actorId,
            String title,
            String taskType,
            UUID facilityId,
            Long outbreakId,
            Long caseId,
            String assignedTo,
            Double latitude,
            Double longitude,
            LocalDate dueDate,
            Map<String, Object> details) {

        FieldTaskEntity task = new FieldTaskEntity();
        task.setTenantId(tenantId);
        task.setTitle(title != null && !title.isBlank() ? title : "Field task");
        task.setTaskType(taskType != null && !taskType.isBlank() ? taskType : "FIELD_OPERATION");
        task.setFacilityId(facilityId);
        task.setOutbreakId(outbreakId);
        task.setCaseId(caseId);
        task.setAssignedTo(assignedTo);
        task.setLatitude(latitude);
        task.setLongitude(longitude);
        task.setDueDate(dueDate);
        task.setStatus(assignedTo != null && !assignedTo.isBlank() ? FieldTaskStatus.ASSIGNED : FieldTaskStatus.PLANNED);
        task.setCreatedBy(actorId);
        task.setDetails(serializeDetails(details));
        return fieldTaskRepository.save(task);
    }

    @Transactional
    public FieldTaskEntity transition(UUID tenantId, Long taskId, FieldTaskStatus nextStatus, String assignedTo) {
        FieldTaskEntity task = fieldTaskRepository.findById(taskId)
                .filter(t -> tenantId.equals(t.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Field task not found"));
        validateTaskTransition(task.getStatus(), nextStatus);
        task.setStatus(nextStatus);
        if (assignedTo != null && !assignedTo.isBlank()) {
            task.setAssignedTo(assignedTo);
        }
        if (nextStatus == FieldTaskStatus.COMPLETED) {
            task.setCompletedAt(OffsetDateTime.now());
        }
        return fieldTaskRepository.save(task);
    }

    private void validateTaskTransition(FieldTaskStatus current, FieldTaskStatus next) {
        if (current == next) {
            return;
        }
        Map<FieldTaskStatus, List<FieldTaskStatus>> allowed = Map.of(
                FieldTaskStatus.PLANNED, List.of(FieldTaskStatus.ASSIGNED, FieldTaskStatus.CANCELLED),
                FieldTaskStatus.ASSIGNED, List.of(FieldTaskStatus.IN_PROGRESS, FieldTaskStatus.CANCELLED),
                FieldTaskStatus.IN_PROGRESS, List.of(FieldTaskStatus.COMPLETED, FieldTaskStatus.CANCELLED),
                FieldTaskStatus.COMPLETED, List.of(),
                FieldTaskStatus.CANCELLED, List.of());
        if (!allowed.getOrDefault(current, List.of()).contains(next)) {
            throw new IllegalArgumentException("Invalid field task transition from " + current + " to " + next);
        }
    }

    private String serializeDetails(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
