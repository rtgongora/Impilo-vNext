package zw.gov.mohcc.impilo.pct.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.api.dto.CreateTaskRequest;
import zw.gov.mohcc.impilo.pct.core.TaskService;
import zw.gov.mohcc.impilo.pct.persistence.entity.TaskEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for clinical and administrative task management.
 *
 * <p>Provides endpoints for creating tasks, completing tasks, and
 * querying tasks by assignee (current actor), workspace, or journey.
 * Tasks represent discrete work items within the patient care workflow.</p>
 */
@RestController
@RequestMapping("/v1/tasks")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Create a new task.
     *
     * @param request the task creation request
     * @return the newly created task entity
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TaskEntity>> createTask(
            @Valid @RequestBody CreateTaskRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        TaskEntity task = taskService.createTask(
                request.journeyId(),
                request.encounterId(),
                request.taskType(),
                request.assigneeId(),
                request.assigneeRole(),
                request.workspaceId(),
                request.dueAt(),
                request.notes());

        return ResponseEntity.ok(ApiResponse.ok(task, correlationId));
    }

    /**
     * Complete a task.
     *
     * @param taskId the task identifier
     * @return the completed task entity
     */
    @PostMapping("/{taskId}/complete")
    public ResponseEntity<ApiResponse<TaskEntity>> completeTask(@PathVariable UUID taskId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        TaskEntity task = taskService.completeTask(taskId);

        return ResponseEntity.ok(ApiResponse.ok(task, correlationId));
    }

    /**
     * Retrieve tasks assigned to the current actor.
     *
     * @return list of open tasks for the current actor
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<TaskEntity>>> getMyTasks() {
        TrustContext ctx = TrustContextHolder.require();
        String correlationId = ctx.correlationId().toString();

        List<TaskEntity> tasks = taskService.getTasksForAssignee(ctx.actorId());

        return ResponseEntity.ok(ApiResponse.ok(tasks, correlationId));
    }

    /**
     * Retrieve tasks for a specific workspace.
     *
     * @param workspaceId the workspace to query
     * @return list of open tasks at the workspace
     */
    @GetMapping("/workspace/{workspaceId}")
    public ResponseEntity<ApiResponse<List<TaskEntity>>> getWorkspaceTasks(
            @PathVariable UUID workspaceId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<TaskEntity> tasks = taskService.getTasksForWorkspace(workspaceId);

        return ResponseEntity.ok(ApiResponse.ok(tasks, correlationId));
    }

    /**
     * Retrieve all tasks for a specific journey.
     *
     * @param journeyId the journey identifier
     * @return list of all tasks (including completed) for the journey
     */
    @GetMapping("/journey/{journeyId}")
    public ResponseEntity<ApiResponse<List<TaskEntity>>> getJourneyTasks(
            @PathVariable String journeyId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<TaskEntity> tasks = taskService.getTasksForJourney(journeyId);

        return ResponseEntity.ok(ApiResponse.ok(tasks, correlationId));
    }
}
