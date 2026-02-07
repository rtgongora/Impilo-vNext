package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.WorkstepStatus;
import zw.gov.mohcc.impilo.oros.domain.WorkstepType;
import zw.gov.mohcc.impilo.oros.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.WorkstepEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.WorkstepRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Manages order workstep creation and progression.
 *
 * <p>Each order type has a predefined template of worksteps. The engine
 * creates the full template when an order is placed and allows operators
 * to start and complete individual worksteps in sequence.</p>
 */
@Service
public class WorkstepEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkstepEngine.class);

    private static final Map<OrderType, List<WorkstepType>> TEMPLATES = Map.of(
            OrderType.LAB, List.of(
                    WorkstepType.ORDER_PLACED,
                    WorkstepType.SPECIMEN_COLLECTION,
                    WorkstepType.SPECIMEN_RECEIVED,
                    WorkstepType.ANALYSIS,
                    WorkstepType.REPORTING,
                    WorkstepType.CLOSE_OUT),
            OrderType.IMAGING, List.of(
                    WorkstepType.ORDER_PLACED,
                    WorkstepType.IMAGING_ACQUISITION,
                    WorkstepType.IMAGING_REPORTING,
                    WorkstepType.CLOSE_OUT),
            OrderType.PHARMACY, List.of(
                    WorkstepType.ORDER_PLACED,
                    WorkstepType.DISPENSE,
                    WorkstepType.CLOSE_OUT),
            OrderType.PROCEDURE, List.of(
                    WorkstepType.ORDER_PLACED,
                    WorkstepType.REPORTING,
                    WorkstepType.CLOSE_OUT),
            OrderType.OTHER, List.of(
                    WorkstepType.ORDER_PLACED,
                    WorkstepType.REPORTING,
                    WorkstepType.CLOSE_OUT)
    );

    private final WorkstepRepository workstepRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public WorkstepEngine(WorkstepRepository workstepRepository,
                          EventOutboxRepository outboxRepository,
                          ObjectMapper objectMapper) {
        this.workstepRepository = workstepRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Create all worksteps for an order based on its type template.
     * The first workstep (ORDER_PLACED) is auto-completed.
     */
    @Transactional
    public List<WorkstepEntity> createWorkstepsForOrder(OrderEntity order) {
        List<WorkstepType> template = TEMPLATES.getOrDefault(order.getOrderType(),
                TEMPLATES.get(OrderType.OTHER));

        List<WorkstepEntity> steps = new ArrayList<>();
        for (int i = 0; i < template.size(); i++) {
            WorkstepEntity step = new WorkstepEntity();
            step.setOrderId(order.getOrderId());
            step.setStepType(template.get(i));
            step.setStatus(i == 0 ? WorkstepStatus.COMPLETED : WorkstepStatus.PENDING);
            if (i == 0) {
                step.setStartedAt(OffsetDateTime.now());
                step.setCompletedAt(OffsetDateTime.now());
            }
            steps.add(workstepRepository.save(step));
        }

        log.info("Created {} worksteps for order {}", steps.size(), order.getOrderId());
        return steps;
    }

    /**
     * Start a workstep. Only PENDING worksteps can be started.
     */
    @Transactional
    public WorkstepEntity startWorkstep(UUID workstepId) {
        TrustContext ctx = TrustContextHolder.require();

        WorkstepEntity step = workstepRepository.findById(workstepId)
                .orElseThrow(() -> new IllegalArgumentException("Workstep not found: " + workstepId));

        if (step.getStatus() != WorkstepStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot start workstep in status " + step.getStatus());
        }

        step.setStatus(WorkstepStatus.IN_PROGRESS);
        step.setStartedAt(OffsetDateTime.now());
        step.setAssignedTo(ctx.actorId());
        step = workstepRepository.save(step);

        publishEvent("WORKSTEP", step.getWorkstepId().toString(),
                "WORKSTEP_STARTED", step, ctx.tenantId());

        log.info("Workstep started: id={}, type={}, order={}",
                workstepId, step.getStepType(), step.getOrderId());
        return step;
    }

    /**
     * Complete a workstep. Only IN_PROGRESS worksteps can be completed.
     */
    @Transactional
    public WorkstepEntity completeWorkstep(UUID workstepId) {
        TrustContext ctx = TrustContextHolder.require();

        WorkstepEntity step = workstepRepository.findById(workstepId)
                .orElseThrow(() -> new IllegalArgumentException("Workstep not found: " + workstepId));

        if (step.getStatus() != WorkstepStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Cannot complete workstep in status " + step.getStatus());
        }

        step.setStatus(WorkstepStatus.COMPLETED);
        step.setCompletedAt(OffsetDateTime.now());
        step = workstepRepository.save(step);

        publishEvent("WORKSTEP", step.getWorkstepId().toString(),
                "WORKSTEP_COMPLETED", step, ctx.tenantId());

        log.info("Workstep completed: id={}, type={}, order={}",
                workstepId, step.getStepType(), step.getOrderId());
        return step;
    }

    /**
     * Get all worksteps for an order, ordered by creation time.
     */
    public List<WorkstepEntity> getWorksteps(String orderId) {
        return workstepRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    /**
     * Get the current (first PENDING or IN_PROGRESS) workstep for an order.
     */
    public Optional<WorkstepEntity> getCurrentWorkstep(String orderId) {
        Optional<WorkstepEntity> inProgress = workstepRepository
                .findFirstByOrderIdAndStatusOrderByCreatedAtAsc(orderId, WorkstepStatus.IN_PROGRESS);
        if (inProgress.isPresent()) {
            return inProgress;
        }
        return workstepRepository
                .findFirstByOrderIdAndStatusOrderByCreatedAtAsc(orderId, WorkstepStatus.PENDING);
    }

    private void publishEvent(String aggregateType, String aggregateId,
                               String eventType, Object payload, UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", eventType, e);
        }
    }
}
