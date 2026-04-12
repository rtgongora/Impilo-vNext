package zw.gov.mohcc.impilo.air.api;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.CreateInferenceRequest;
import zw.gov.mohcc.impilo.air.api.dto.ModelDtos.InferenceResponse;
import zw.gov.mohcc.impilo.air.service.InferenceRecordService;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;

@RestController
@RequestMapping("/internal/v1/ai-registry/inference-records")
public class InferenceRecordController {

    private final InferenceRecordService inferenceRecordService;

    public InferenceRecordController(InferenceRecordService inferenceRecordService) {
        this.inferenceRecordService = inferenceRecordService;
    }

    @PostMapping
    public ResponseEntity<InferenceResponse> create(
            @Valid @RequestBody CreateInferenceRequest body,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey) {
        InferenceResponse r =
                inferenceRecordService.record(body, RequestContextHolder.require(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(r);
    }

    @GetMapping
    public ResponseEntity<List<InferenceResponse>> query(
            @RequestParam(value = "workflow_ref", required = false) String workflowRef,
            @RequestParam(value = "subject_type", required = false) String subjectType,
            @RequestParam(value = "subject_id", required = false) String subjectId) {
        if (workflowRef != null && !workflowRef.isBlank()) {
            return ResponseEntity.ok(
                    inferenceRecordService.findByWorkflow(workflowRef, RequestContextHolder.require()));
        }
        if (subjectType != null && !subjectType.isBlank() && subjectId != null && !subjectId.isBlank()) {
            return ResponseEntity.ok(
                    inferenceRecordService.findBySubject(subjectType, subjectId, RequestContextHolder.require()));
        }
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Provide workflow_ref or both subject_type and subject_id");
    }
}
