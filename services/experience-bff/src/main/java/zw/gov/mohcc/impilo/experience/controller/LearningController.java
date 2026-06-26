package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.LearningServiceClient;
import zw.gov.mohcc.impilo.experience.client.NotificationServiceClient;

@RestController
@RequestMapping("/internal/v1/learning")
public class LearningController {

    private final LearningServiceClient learningClient;
    private final NotificationServiceClient notificationClient;
    private final RestTemplate restTemplate;
    private final String llmBaseUrl;

    public LearningController(
            LearningServiceClient learningClient,
            NotificationServiceClient notificationClient,
            RestTemplate serviceRestTemplate,
            @org.springframework.beans.factory.annotation.Value("${impilo.services.llm-orchestration-base-url:http://localhost:8265}") String llmBaseUrl) {
        this.learningClient = learningClient;
        this.notificationClient = notificationClient;
        this.restTemplate = serviceRestTemplate;
        this.llmBaseUrl = llmBaseUrl;
    }

    @GetMapping("/workflow-context")
    public ResponseEntity<Map<String, Object>> workflowContext(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String appCode,
            @RequestParam String routeRef,
            @RequestParam(required = false) String workflowCode,
            @RequestParam(required = false) String roles,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId) {
        JsonNode n = learningClient.getWorkflowContext(appCode, routeRef, workflowCode, roles, subjectType, subjectId);
        JsonNode payload = n != null ? n : JsonNodeFactory.instance.objectNode();
        return ResponseEntity.ok(Map.of("data", payload));
    }

    @GetMapping("/helpdesk/{issueType}")
    public ResponseEntity<Map<String, Object>> helpdesk(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String issueType,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId) {
        JsonNode n = learningClient.getHelpdeskLearning(issueType, subjectType, subjectId);
        JsonNode payload = n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode());
        return ResponseEntity.ok(Map.of("data", payload));
    }

    @PostMapping("/resource-opened")
    public ResponseEntity<Map<String, Object>> resourceOpened(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId, @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postResourceOpened(body);
        JsonNode payload = n != null ? n : JsonNodeFactory.instance.objectNode().put("status", "skipped");
        return ResponseEntity.ok(Map.of("data", payload));
    }

    @GetMapping("/subject-completions")
    public ResponseEntity<Map<String, Object>> subjectCompletions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String subjectType,
            @RequestParam String subjectId,
            @RequestParam(defaultValue = "25") int limit) {
        JsonNode n = learningClient.getSubjectCompletions(subjectType, subjectId, limit);
        JsonNode payload = n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode());
        return ResponseEntity.ok(Map.of("data", payload));
    }

    @GetMapping("/resources/{resourceId}")
    public ResponseEntity<Map<String, Object>> resource(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId, @PathVariable String resourceId) {
        JsonNode n = learningClient.getResource(resourceId);
        if (n == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("data", n));
    }

    @PostMapping("/subject-profile")
    public ResponseEntity<Map<String, Object>> subjectProfile(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId, @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postSubjectProfile(body);
        JsonNode payload = n != null ? n : JsonNodeFactory.instance.objectNode().put("status", "skipped");
        return ResponseEntity.ok(Map.of("data", payload));
    }

    /**
     * Phase 6B — One Experience Shell delivery layer BFF surface for the
     * Phase 5B native Fundo catalogue. Thin passthrough that forwards the
     * tenant context and the optional filters to {@code learning-service},
     * returns the {@code {"data": ...}} envelope unmodified, and falls back
     * to an empty items list when the upstream is unavailable so the shell
     * can render a graceful empty state instead of a hard failure.
     */
    @GetMapping("/v11/catalog")
    public ResponseEntity<Map<String, Object>> v11Catalog(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Boolean cpdEligible,
            @RequestParam(required = false) Boolean mandatory,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "25") int limit) {
        JsonNode n = learningClient.getV11Catalog(status, category, level, cpdEligible, mandatory, language, limit);
        if (n != null) {
            return ResponseEntity.ok(Map.of("data", n));
        }
        JsonNodeFactory f = JsonNodeFactory.instance;
        return ResponseEntity.ok(Map.of(
                "data", f.objectNode().put("limit", limit).set("items", f.arrayNode())));
    }

    /**
     * Fundo language metadata — passthrough to {@code learning-service}
     * {@code GET /internal/v1/learning/v11/metadata/languages}. Returns
     * {@code {"data":{"items":[...]}}} for {@code useFundoLanguageOptions()}.
     */
    @GetMapping("/v11/metadata/languages")
    public ResponseEntity<Map<String, Object>> v11MetadataLanguages(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        JsonNode n = learningClient.getV11("metadata/languages", Map.of());
        JsonNodeFactory f = JsonNodeFactory.instance;
        return ResponseEntity.ok(Map.of(
                "data", n != null ? n : f.objectNode().set("items", f.arrayNode())));
    }

    @GetMapping("/v11/catalog/{courseId}")
    public ResponseEntity<Map<String, Object>> v11Course(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String courseId) {
        JsonNode n = learningClient.getV11Course(courseId);
        if (n == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("data", n));
    }

    @GetMapping("/v11/courses/{courseId}/structure")
    public ResponseEntity<Map<String, Object>> v11CourseStructure(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String courseId) {
        JsonNode n = learningClient.getV11CourseStructure(courseId);
        if (n == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("data", n));
    }

    @GetMapping("/v11/my-learning")
    public ResponseEntity<Map<String, Object>> myLearning(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        JsonNode n = learningClient.getV11("my-learning", Map.of("subjectType", subjectType, "subjectId", subjectId));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/pathways")
    public ResponseEntity<Map<String, Object>> pathways(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "25") int limit) {
        JsonNode n = learningClient.getV11("pathways", qp("status", status, "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/v11/pathways/{pathwayId}")
    public ResponseEntity<Map<String, Object>> pathway(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String pathwayId) {
        JsonNode n = learningClient.getV11("pathways/" + pathwayId, Map.of());
        if (n == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", n));
    }

    @PostMapping("/v11/enrolments")
    public ResponseEntity<Map<String, Object>> createEnrolment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("enrolments", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/enrolments")
    public ResponseEntity<Map<String, Object>> listEnrolments(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String subjectType,
            @RequestParam String subjectId,
            @RequestParam(defaultValue = "25") int limit) {
        JsonNode n = learningClient.getV11("enrolments", Map.of(
                "subjectType", subjectType,
                "subjectId", subjectId,
                "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/v11/enrolments/{enrolmentId}")
    public ResponseEntity<Map<String, Object>> getEnrolment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String enrolmentId) {
        JsonNode n = learningClient.getV11("enrolments/" + enrolmentId, Map.of());
        if (n == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", n));
    }

    @PostMapping("/v11/enrolments/{enrolmentId}/start")
    public ResponseEntity<Map<String, Object>> startEnrolment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String enrolmentId) {
        JsonNode n = learningClient.postV11("enrolments/" + enrolmentId + "/start", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/enrolments/{enrolmentId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelEnrolment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String enrolmentId,
            @RequestBody(required = false) Map<String, Object> body) {
        JsonNode n = learningClient.postV11("enrolments/" + enrolmentId + "/cancel", body == null ? Map.of() : body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/enrolments/{enrolmentId}/progress")
    public ResponseEntity<Map<String, Object>> enrolmentProgress(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String enrolmentId) {
        JsonNode n = learningClient.getV11("enrolments/" + enrolmentId + "/progress", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/progress")
    public ResponseEntity<Map<String, Object>> recordProgress(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("progress", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/progress")
    public ResponseEntity<Map<String, Object>> listProgress(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        JsonNode n = learningClient.getV11("progress", Map.of("subjectType", subjectType, "subjectId", subjectId));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/lessons/{lessonId}/open")
    public ResponseEntity<Map<String, Object>> openLesson(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String lessonId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("lessons/" + lessonId + "/open", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/courses/{courseId}/assessments")
    public ResponseEntity<Map<String, Object>> listCourseAssessments(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String courseId) {
        JsonNode n = learningClient.getV11("courses/" + courseId + "/assessments", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/v11/assessments/{assessmentId}")
    public ResponseEntity<Map<String, Object>> getAssessment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assessmentId) {
        JsonNode n = learningClient.getV11("assessments/" + assessmentId, Map.of());
        if (n == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", n));
    }

    @PostMapping("/v11/assessments/{assessmentId}/attempts")
    public ResponseEntity<Map<String, Object>> submitAssessmentAttempt(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assessmentId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("assessments/" + assessmentId + "/attempts", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/assessments/{assessmentId}/attempts")
    public ResponseEntity<Map<String, Object>> listAssessmentAttempts(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assessmentId,
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        JsonNode n = learningClient.getV11("assessments/" + assessmentId + "/attempts", Map.of(
                "subjectType", subjectType,
                "subjectId", subjectId));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/v11/attempts/{attemptId}")
    public ResponseEntity<Map<String, Object>> getAttempt(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String attemptId) {
        JsonNode n = learningClient.getV11("attempts/" + attemptId, Map.of());
        if (n == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", n));
    }

    @GetMapping("/v11/assessments/{assessmentId}/pending-reviews")
    public ResponseEntity<Map<String, Object>> pendingAssessmentReviews(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assessmentId,
            @RequestParam(required = false) Integer limit) {
        JsonNode n = learningClient.getV11("assessments/" + assessmentId + "/pending-reviews", qp("limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/attempts/{attemptId}/manual-review")
    public ResponseEntity<Map<String, Object>> recordManualAssessmentReview(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String attemptId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("attempts/" + attemptId + "/manual-review", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/certificates")
    public ResponseEntity<Map<String, Object>> listCertificates(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        JsonNode n = learningClient.getV11("certificates", Map.of("subjectType", subjectType, "subjectId", subjectId));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/v11/certificates/{certificateId}")
    public ResponseEntity<Map<String, Object>> getCertificate(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String certificateId) {
        JsonNode n = learningClient.getV11("certificates/" + certificateId, Map.of());
        if (n == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", n));
    }

    @PostMapping("/v11/enrolments/{enrolmentId}/certificate")
    public ResponseEntity<Map<String, Object>> issueCertificate(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String enrolmentId) {
        JsonNode n = learningClient.postV11("enrolments/" + enrolmentId + "/certificate", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/cpd/evidence")
    public ResponseEntity<Map<String, Object>> cpdEvidence(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        JsonNode n = learningClient.getV11("cpd/evidence", Map.of("subjectType", subjectType, "subjectId", subjectId));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/cpd/eligible-completions")
    public ResponseEntity<Map<String, Object>> cpdEligibleCompletions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        JsonNode n = learningClient.getV11("cpd/eligible-completions", Map.of("subjectType", subjectType, "subjectId", subjectId));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/v11/reports/overview")
    public ResponseEntity<Map<String, Object>> reportOverview(@RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        JsonNode n = learningClient.getV11("reports/overview", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/reports/cohort-completions")
    public ResponseEntity<Map<String, Object>> reportCohortCompletions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String pathwayId,
            @RequestParam(required = false) String courseId) {
        JsonNode n = learningClient.getV11("reports/cohort-completions", qp("pathwayId", pathwayId, "courseId", courseId));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/v11/reports/course-completions")
    public ResponseEntity<Map<String, Object>> reportCourseCompletions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {
        JsonNode n = learningClient.getV11("reports/course-completions",
                qp("courseId", courseId, "subjectType", subjectType, "status", status, "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/v11/reports/overdue-learning")
    public ResponseEntity<Map<String, Object>> reportOverdueLearning(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {
        JsonNode n = learningClient.getV11("reports/overdue-learning",
                qp("courseId", courseId, "subjectType", subjectType, "status", status, "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/v11/reports/assessment-performance")
    public ResponseEntity<Map<String, Object>> reportAssessmentPerformance(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) Integer limit) {
        JsonNode n = learningClient.getV11("reports/assessment-performance",
                qp("courseId", courseId, "subjectType", subjectType, "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/catalog")
    public ResponseEntity<Map<String, Object>> createCourse(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("catalog", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PutMapping("/v11/catalog/{courseId}")
    public ResponseEntity<Map<String, Object>> updateCourse(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String courseId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.putV11("catalog/" + courseId, body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/courses/{courseId}/modules")
    public ResponseEntity<Map<String, Object>> createModule(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String courseId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("courses/" + courseId + "/modules", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PutMapping("/v11/modules/{moduleId}")
    public ResponseEntity<Map<String, Object>> updateModule(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String moduleId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.putV11("modules/" + moduleId, body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/modules/{moduleId}/lessons")
    public ResponseEntity<Map<String, Object>> createLesson(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String moduleId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("modules/" + moduleId + "/lessons", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PutMapping("/v11/lessons/{lessonId}")
    public ResponseEntity<Map<String, Object>> updateLesson(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String lessonId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.putV11("lessons/" + lessonId, body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/pathways")
    public ResponseEntity<Map<String, Object>> createPathway(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("pathways", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PutMapping("/v11/pathways/{pathwayId}")
    public ResponseEntity<Map<String, Object>> updatePathway(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String pathwayId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.putV11("pathways/" + pathwayId, body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/pathways/{pathwayId}/items")
    public ResponseEntity<Map<String, Object>> addPathwayItem(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String pathwayId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("pathways/" + pathwayId + "/items", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/assessments")
    public ResponseEntity<Map<String, Object>> createAssessment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("assessments", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PutMapping("/v11/assessments/{assessmentId}")
    public ResponseEntity<Map<String, Object>> updateAssessment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assessmentId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.putV11("assessments/" + assessmentId, body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/assessments/{assessmentId}/questions")
    public ResponseEntity<Map<String, Object>> addAssessmentQuestion(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assessmentId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("assessments/" + assessmentId + "/questions", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PutMapping("/v11/assessments/{assessmentId}/questions/{questionId}")
    public ResponseEntity<Map<String, Object>> updateAssessmentQuestion(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assessmentId,
            @PathVariable String questionId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.putV11("assessments/" + assessmentId + "/questions/" + questionId, body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/subjects/{subjectType}/{subjectId}/record")
    public ResponseEntity<Map<String, Object>> record(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String subjectType,
            @PathVariable String subjectId) {
        JsonNode n = learningClient.getV11("subjects/" + subjectType + "/" + subjectId + "/record", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/studio/dashboard")
    public ResponseEntity<Map<String, Object>> studioDashboard(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        JsonNode n = learningClient.getV11("studio/dashboard", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/studio/courses/{courseId}/readiness")
    public ResponseEntity<Map<String, Object>> studioReadiness(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String courseId) {
        JsonNode n = learningClient.getV11("studio/courses/" + courseId + "/readiness", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/ai/generate")
    public ResponseEntity<Map<String, Object>> aiGenerate(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("ai/generate", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/nompilo/assist")
    public ResponseEntity<Map<String, Object>> nompiloAssist(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = tryStructuredNompiloAdapter(tenantId, actorId, body);
        if (n == null) {
            n = learningClient.postV11("nompilo/assist", body);
        }
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().put("mode", "stub")));
    }

    @GetMapping("/v11/library/resources")
    public ResponseEntity<Map<String, Object>> listLibraryResources(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(defaultValue = "50") int limit) {
        JsonNode n = learningClient.getV11("library/resources", Map.of("limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/library/resources")
    public ResponseEntity<Map<String, Object>> createLibraryResource(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("library/resources", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/library/resources/{resourceId}/links")
    public ResponseEntity<Map<String, Object>> linkLibraryResource(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String resourceId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("library/resources/" + resourceId + "/links", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/library/uploads")
    public ResponseEntity<Map<String, Object>> uploadLibraryResource(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("library/uploads", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/media/assets")
    public ResponseEntity<Map<String, Object>> listMediaAssets(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(defaultValue = "50") int limit) {
        JsonNode n = learningClient.getV11("media/assets", Map.of("limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/media/assets")
    public ResponseEntity<Map<String, Object>> createMediaAsset(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("media/assets", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/notifications")
    public ResponseEntity<Map<String, Object>> listLearningNotifications(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String subjectType,
            @RequestParam String subjectId,
            @RequestParam(defaultValue = "50") int limit) {
        JsonNode n = learningClient.getV11("notifications", Map.of(
                "subjectType", subjectType,
                "subjectId", subjectId,
                "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/notifications")
    public ResponseEntity<Map<String, Object>> scheduleLearningNotification(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("notifications", body);
        JsonNode commsDispatch = dispatchToCommsHub(body);
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        out.set("learning", n != null ? n : JsonNodeFactory.instance.objectNode());
        out.set("commsHubDispatch", commsDispatch != null ? commsDispatch : JsonNodeFactory.instance.objectNode().put("status", "not-dispatched"));
        return ResponseEntity.ok(Map.of("data", out));
    }

    @GetMapping("/v11/interactive/activities")
    public ResponseEntity<Map<String, Object>> listInteractiveActivities(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String lessonId,
            @RequestParam(defaultValue = "50") int limit) {
        JsonNode n = learningClient.getV11("interactive/activities", qp(
                "courseId", courseId,
                "lessonId", lessonId,
                "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/interactive/activities")
    public ResponseEntity<Map<String, Object>> createInteractiveActivity(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("interactive/activities", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/interactive/activities/{activityId}/responses")
    public ResponseEntity<Map<String, Object>> listInteractiveResponses(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String activityId,
            @RequestParam(defaultValue = "50") int limit) {
        JsonNode n = learningClient.getV11("interactive/activities/" + activityId + "/responses", Map.of("limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/interactive/activities/{activityId}/responses")
    public ResponseEntity<Map<String, Object>> submitInteractiveResponse(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String activityId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("interactive/activities/" + activityId + "/responses", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/cohorts")
    public ResponseEntity<Map<String, Object>> listCohorts(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(defaultValue = "50") int limit) {
        JsonNode n = learningClient.getV11("cohorts", Map.of("limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/cohorts")
    public ResponseEntity<Map<String, Object>> createCohort(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("cohorts", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/cohorts/{cohortId}/members")
    public ResponseEntity<Map<String, Object>> addCohortMember(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String cohortId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("cohorts/" + cohortId + "/members", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/sessions")
    public ResponseEntity<Map<String, Object>> listSessions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(defaultValue = "50") int limit) {
        JsonNode n = learningClient.getV11("sessions", Map.of("limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("sessions", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    // ---- A1: learning-delivery administration (facilitators, venues, cohort-facilitators, session delivery) ----

    @GetMapping("/v11/facilitators")
    public ResponseEntity<Map<String, Object>> listFacilitators(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "100") int limit) {
        JsonNode n = learningClient.getV11("facilitators", qp("kind", kind, "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/facilitators")
    public ResponseEntity<Map<String, Object>> createFacilitator(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("facilitators", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/facilitators/{id}/status")
    public ResponseEntity<Map<String, Object>> updateFacilitatorStatus(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("facilitators/" + id + "/status", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/venues")
    public ResponseEntity<Map<String, Object>> listVenues(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(defaultValue = "100") int limit) {
        JsonNode n = learningClient.getV11("venues", Map.of("limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/venues")
    public ResponseEntity<Map<String, Object>> createVenue(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("venues", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/cohorts/{cohortId}/facilitators")
    public ResponseEntity<Map<String, Object>> listCohortFacilitators(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String cohortId) {
        JsonNode n = learningClient.getV11("cohorts/" + cohortId + "/facilitators", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/cohorts/{cohortId}/facilitators")
    public ResponseEntity<Map<String, Object>> assignCohortFacilitator(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String cohortId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("cohorts/" + cohortId + "/facilitators", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/sessions/{sessionId}/delivery")
    public ResponseEntity<Map<String, Object>> assignSessionDelivery(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("sessions/" + sessionId + "/delivery", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    // ---- A2: attendance + check-in ----

    @PostMapping("/v11/sessions/{sessionId}/checkin-tokens")
    public ResponseEntity<Map<String, Object>> createCheckinToken(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body) {
        JsonNode n = learningClient.postV11("sessions/" + sessionId + "/checkin-tokens", body == null ? Map.of() : body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/sessions/{sessionId}/attendance")
    public ResponseEntity<Map<String, Object>> markAttendance(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("sessions/" + sessionId + "/attendance", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/sessions/{sessionId}/checkin")
    public ResponseEntity<Map<String, Object>> selfCheckin(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("sessions/" + sessionId + "/checkin", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/sessions/{sessionId}/attendance")
    public ResponseEntity<Map<String, Object>> listAttendance(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String sessionId) {
        JsonNode n = learningClient.getV11("sessions/" + sessionId + "/attendance", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    // ---- A3: assignments + marking ----

    @GetMapping("/v11/assignments")
    public ResponseEntity<Map<String, Object>> listAssignments(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String cohortId,
            @RequestParam(defaultValue = "100") int limit) {
        JsonNode n = learningClient.getV11("assignments", qp("courseId", courseId, "cohortId", cohortId, "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/assignments")
    public ResponseEntity<Map<String, Object>> createAssignment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("assignments", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/assignments/{assignmentId}")
    public ResponseEntity<Map<String, Object>> getAssignment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assignmentId) {
        JsonNode n = learningClient.getV11("assignments/" + assignmentId, Map.of());
        if (n == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", n));
    }

    @PostMapping("/v11/assignments/{assignmentId}/submissions")
    public ResponseEntity<Map<String, Object>> submitAssignment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assignmentId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("assignments/" + assignmentId + "/submissions", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/assignments/{assignmentId}/submissions")
    public ResponseEntity<Map<String, Object>> listSubmissions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assignmentId) {
        JsonNode n = learningClient.getV11("assignments/" + assignmentId + "/submissions", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/v11/marking-queue")
    public ResponseEntity<Map<String, Object>> markingQueue(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String cohortId,
            @RequestParam(defaultValue = "100") int limit) {
        JsonNode n = learningClient.getV11("marking-queue", qp("cohortId", cohortId, "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/submissions/{submissionId}/mark")
    public ResponseEntity<Map<String, Object>> markSubmission(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String submissionId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("submissions/" + submissionId + "/mark", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    // ---- A4: academic foundation (programs + terms) ----

    @GetMapping("/v11/academic/programs")
    public ResponseEntity<Map<String, Object>> listPrograms(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(defaultValue = "100") int limit) {
        JsonNode n = learningClient.getV11("academic/programs", Map.of("limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/academic/programs")
    public ResponseEntity<Map<String, Object>> createProgram(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("academic/programs", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/academic/programs/{programId}")
    public ResponseEntity<Map<String, Object>> getProgram(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String programId) {
        JsonNode n = learningClient.getV11("academic/programs/" + programId, Map.of());
        if (n == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", n));
    }

    @GetMapping("/v11/academic/terms")
    public ResponseEntity<Map<String, Object>> listTerms(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String programId,
            @RequestParam(defaultValue = "100") int limit) {
        JsonNode n = learningClient.getV11("academic/terms", qp("programId", programId, "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/academic/terms")
    public ResponseEntity<Map<String, Object>> createTerm(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("academic/terms", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    // ---- A5: admissions + student registry ----

    @GetMapping("/v11/admissions/applications")
    public ResponseEntity<Map<String, Object>> listApplications(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String programId,
            @RequestParam(defaultValue = "100") int limit) {
        JsonNode n = learningClient.getV11("admissions/applications", qp("status", status, "programId", programId, "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/admissions/applications")
    public ResponseEntity<Map<String, Object>> submitApplication(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("admissions/applications", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/admissions/applications/{applicationId}/decision")
    public ResponseEntity<Map<String, Object>> decideApplication(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String applicationId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("admissions/applications/" + applicationId + "/decision", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/students")
    public ResponseEntity<Map<String, Object>> listStudents(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String programId,
            @RequestParam(defaultValue = "100") int limit) {
        JsonNode n = learningClient.getV11("students", qp("programId", programId, "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/students")
    public ResponseEntity<Map<String, Object>> admitStudent(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("students", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/students/{studentId}")
    public ResponseEntity<Map<String, Object>> getStudent(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String studentId) {
        JsonNode n = learningClient.getV11("students/" + studentId, Map.of());
        if (n == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", n));
    }

    // ---- A6: registration / placement / graduation ----

    @PostMapping("/v11/students/{studentId}/registrations")
    public ResponseEntity<Map<String, Object>> registerCourse(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String studentId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("students/" + studentId + "/registrations", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/students/{studentId}/registrations")
    public ResponseEntity<Map<String, Object>> listRegistrations(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String studentId) {
        JsonNode n = learningClient.getV11("students/" + studentId + "/registrations", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/students/{studentId}/placements")
    public ResponseEntity<Map<String, Object>> createPlacement(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String studentId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("students/" + studentId + "/placements", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/students/{studentId}/placements")
    public ResponseEntity<Map<String, Object>> listPlacements(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String studentId) {
        JsonNode n = learningClient.getV11("students/" + studentId + "/placements", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/placements/{placementId}/signoff")
    public ResponseEntity<Map<String, Object>> signoffPlacement(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String placementId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("placements/" + placementId + "/signoff", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/students/{studentId}/graduate")
    public ResponseEntity<Map<String, Object>> graduate(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String studentId,
            @RequestBody(required = false) Map<String, Object> body) {
        JsonNode n = learningClient.postV11("students/" + studentId + "/graduate", body == null ? Map.of() : body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/students/{studentId}/academic-record")
    public ResponseEntity<Map<String, Object>> academicRecord(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String studentId) {
        JsonNode n = learningClient.getV11("students/" + studentId + "/academic-record", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    // ---- C1: learning-provider & academy registry ----

    @GetMapping("/v11/providers")
    public ResponseEntity<Map<String, Object>> listProviders(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "100") int limit) {
        JsonNode n = learningClient.getV11("providers", qp("kind", kind, "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/providers")
    public ResponseEntity<Map<String, Object>> createProvider(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("providers", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/providers/directory")
    public ResponseEntity<Map<String, Object>> providerDirectory(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(defaultValue = "200") int limit) {
        JsonNode n = learningClient.getV11("providers/directory", Map.of("limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/v11/providers/{providerId}")
    public ResponseEntity<Map<String, Object>> getProvider(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String providerId) {
        JsonNode n = learningClient.getV11("providers/" + providerId, Map.of());
        if (n == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", n));
    }

    @GetMapping("/v11/providers/{providerId}/spaces")
    public ResponseEntity<Map<String, Object>> listSpaces(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String providerId) {
        JsonNode n = learningClient.getV11("providers/" + providerId + "/spaces", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/providers/{providerId}/spaces")
    public ResponseEntity<Map<String, Object>> createSpace(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String providerId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("providers/" + providerId + "/spaces", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/providers/{providerId}/accreditations")
    public ResponseEntity<Map<String, Object>> listAccreditations(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String providerId) {
        JsonNode n = learningClient.getV11("providers/" + providerId + "/accreditations", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    // ---- C2: provider accreditation workflow ----

    @GetMapping("/v11/provider-applications")
    public ResponseEntity<Map<String, Object>> listProviderApplications(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit) {
        JsonNode n = learningClient.getV11("provider-applications", qp("status", status, "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/v11/provider-applications")
    public ResponseEntity<Map<String, Object>> submitProviderApplication(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("provider-applications", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/provider-applications/{applicationId}/decision")
    public ResponseEntity<Map<String, Object>> decideProviderApplication(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String applicationId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("provider-applications/" + applicationId + "/decision", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/v11/provider-applications/{applicationId}/accredit")
    public ResponseEntity<Map<String, Object>> accreditProvider(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String applicationId,
            @RequestBody(required = false) Map<String, Object> body) {
        JsonNode n = learningClient.postV11("provider-applications/" + applicationId + "/accredit", body == null ? Map.of() : body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    // ---- C3: delegated space administration ----

    @PostMapping("/v11/courses/{courseId}/learning-space")
    public ResponseEntity<Map<String, Object>> assignCourseToSpace(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String courseId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postV11("courses/" + courseId + "/learning-space", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/v11/spaces/{spaceId}/courses")
    public ResponseEntity<Map<String, Object>> listSpaceCourses(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String spaceId,
            @RequestParam(defaultValue = "200") int limit) {
        JsonNode n = learningClient.getV11("spaces/" + spaceId + "/courses", Map.of("limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/v11/spaces/{spaceId}/summary")
    public ResponseEntity<Map<String, Object>> spaceSummary(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String spaceId) {
        JsonNode n = learningClient.getV11("spaces/" + spaceId + "/summary", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    private static Map<String, Object> qp(Object... keyVals) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyVals.length; i += 2) {
            Object key = keyVals[i];
            Object val = keyVals[i + 1];
            if (key != null && val != null) {
                out.put(key.toString(), val);
            }
        }
        return out;
    }

    private JsonNode dispatchToCommsHub(Map<String, Object> body) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("channel", body.getOrDefault("channelPreference", "IN_APP"));
            payload.put("title", body.getOrDefault("title", "Learning notification"));
            payload.put("message", body.getOrDefault("message", ""));
            payload.put("recipientId", body.getOrDefault("subjectId", ""));
            payload.put("metadata", body.getOrDefault("metadata", Map.of()));
            return notificationClient.sendNotification(payload);
        } catch (Exception ex) {
            return JsonNodeFactory.instance.objectNode()
                    .put("status", "dispatch-failed")
                    .put("reason", ex.getMessage() == null ? "unknown" : ex.getMessage());
        }
    }

    private JsonNode tryStructuredNompiloAdapter(String tenantId, String actorId, Map<String, Object> body) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("useCase", "NOMPILO_FUNDO_ASSIST");
            payload.put("actorContext", Map.of(
                    "tenantId", tenantId == null ? "" : tenantId,
                    "actorId", actorId == null ? "" : actorId,
                    "purposeOfUse", "LEARNING_EXPERIENCE"));
            payload.put("messages", body.getOrDefault(
                    "messages",
                    java.util.List.of(Map.of(
                            "role", "user",
                            "content", String.valueOf(body.getOrDefault("message", body.getOrDefault("prompt", "")))))));
            payload.put("requiredCapabilities", java.util.List.of("CHAT", "STRUCTURED_OUTPUT"));
            payload.put("riskLevel", String.valueOf(body.getOrDefault("riskLevel", "MODERATE")));
            payload.put("requiresAudit", true);
            payload.put("requiresHumanApprovalForActions", false);
            payload.put("temperature", 0.2);
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.postForEntity(
                    llmBaseUrl + "/internal/v1/llm/structured",
                    payload,
                    (Class<Map<String, Object>>) (Class<?>) Map.class);
            Object responseBody = response.getBody() == null ? null : response.getBody().get("data");
            if (responseBody == null) {
                return null;
            }
            return JsonNodeFactory.instance.pojoNode(responseBody);
        } catch (Exception ex) {
            return null;
        }
    }
}
