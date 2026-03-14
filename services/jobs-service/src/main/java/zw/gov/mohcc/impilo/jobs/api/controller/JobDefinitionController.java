package zw.gov.mohcc.impilo.jobs.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.jobs.api.dto.CreateJobDefinitionRequest;
import zw.gov.mohcc.impilo.jobs.api.dto.TriggerJobRequest;
import zw.gov.mohcc.impilo.jobs.core.JobDefinitionService;
import zw.gov.mohcc.impilo.jobs.core.JobExecutionService;
import zw.gov.mohcc.impilo.jobs.persistence.entity.JobDefinitionEntity;
import zw.gov.mohcc.impilo.jobs.persistence.entity.JobExecutionEntity;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for job definitions and triggering executions.
 * All endpoints are under {@code /internal/v1/jobs}.
 */
@RestController
@RequestMapping("/internal/v1/jobs")
public class JobDefinitionController {

    private static final Logger log = LoggerFactory.getLogger(JobDefinitionController.class);

    private final JobDefinitionService jobDefinitionService;
    private final JobExecutionService jobExecutionService;

    public JobDefinitionController(JobDefinitionService jobDefinitionService,
                                   JobExecutionService jobExecutionService) {
        this.jobDefinitionService = jobDefinitionService;
        this.jobExecutionService = jobExecutionService;
    }

    /**
     * List all job definitions for a given tenant.
     */
    @GetMapping
    public ResponseEntity<List<JobDefinitionEntity>> listJobs(@RequestParam UUID tenantId) {
        List<JobDefinitionEntity> jobs = jobDefinitionService.listByTenant(tenantId);
        return ResponseEntity.ok(jobs);
    }

    /**
     * Get a single job definition by its ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<JobDefinitionEntity> getJob(@PathVariable Long id) {
        JobDefinitionEntity job = jobDefinitionService.getById(id);
        return ResponseEntity.ok(job);
    }

    /**
     * Create a new job definition.
     */
    @PostMapping
    public ResponseEntity<JobDefinitionEntity> createJob(
            @Valid @RequestBody CreateJobDefinitionRequest request) {
        JobDefinitionEntity job = jobDefinitionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    /**
     * Trigger an execution for the given job definition.
     */
    @PostMapping("/{id}/trigger")
    public ResponseEntity<JobExecutionEntity> triggerJob(
            @PathVariable Long id,
            @RequestBody(required = false) TriggerJobRequest request) {
        JobDefinitionEntity jobDefinition = jobDefinitionService.getById(id);
        JobExecutionEntity execution = jobExecutionService.trigger(jobDefinition, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(execution);
    }
}
