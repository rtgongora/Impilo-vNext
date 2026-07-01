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
     * One Experience Shell delivery layer BFF surface for the native Fundo
     * catalogue. It forwards tenant context and optional filters to
     * {@code learning-service}, then falls back to an empty list when the
     * upstream is unavailable.
     */
    @GetMapping("/fundo/catalog")
    public ResponseEntity<Map<String, Object>> fundoCatalog(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) Boolean cpdEligible,
            @RequestParam(required = false) Boolean mandatory,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "25") int limit) {
        JsonNode n = learningClient.getFundoCatalog(status, category, level, cpdEligible, mandatory, language, limit);
        if (n != null) {
            return ResponseEntity.ok(Map.of("data", n));
        }
        JsonNodeFactory f = JsonNodeFactory.instance;
        return ResponseEntity.ok(Map.of(
                "data", f.objectNode().put("limit", limit).set("items", f.arrayNode())));
    }

    @GetMapping("/fundo/catalog/{courseId}")
    public ResponseEntity<Map<String, Object>> fundoCourse(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String courseId) {
        JsonNode n = learningClient.getFundoCourse(courseId);
        if (n == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("data", n));
    }

    @GetMapping("/fundo/courses/{courseId}/structure")
    public ResponseEntity<Map<String, Object>> fundoCourseStructure(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String courseId) {
        JsonNode n = learningClient.getFundoCourseStructure(courseId);
        if (n == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("data", n));
    }

    @GetMapping("/fundo/my-learning")
    public ResponseEntity<Map<String, Object>> myLearning(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        JsonNode n = learningClient.getFundo("my-learning", Map.of("subjectType", subjectType, "subjectId", subjectId));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/pathways")
    public ResponseEntity<Map<String, Object>> pathways(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "25") int limit) {
        JsonNode n = learningClient.getFundo("pathways", qp("status", status, "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/fundo/pathways/{pathwayId}")
    public ResponseEntity<Map<String, Object>> pathway(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String pathwayId) {
        JsonNode n = learningClient.getFundo("pathways/" + pathwayId, Map.of());
        if (n == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", n));
    }

    @PostMapping("/fundo/enrolments")
    public ResponseEntity<Map<String, Object>> createEnrolment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("enrolments", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/enrolments")
    public ResponseEntity<Map<String, Object>> listEnrolments(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String subjectType,
            @RequestParam String subjectId,
            @RequestParam(defaultValue = "25") int limit) {
        JsonNode n = learningClient.getFundo("enrolments", Map.of(
                "subjectType", subjectType,
                "subjectId", subjectId,
                "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/fundo/enrolments/{enrolmentId}")
    public ResponseEntity<Map<String, Object>> getEnrolment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String enrolmentId) {
        JsonNode n = learningClient.getFundo("enrolments/" + enrolmentId, Map.of());
        if (n == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", n));
    }

    @PostMapping("/fundo/enrolments/{enrolmentId}/start")
    public ResponseEntity<Map<String, Object>> startEnrolment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String enrolmentId) {
        JsonNode n = learningClient.postFundo("enrolments/" + enrolmentId + "/start", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/fundo/enrolments/{enrolmentId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelEnrolment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String enrolmentId,
            @RequestBody(required = false) Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("enrolments/" + enrolmentId + "/cancel", body == null ? Map.of() : body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/enrolments/{enrolmentId}/progress")
    public ResponseEntity<Map<String, Object>> enrolmentProgress(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String enrolmentId) {
        JsonNode n = learningClient.getFundo("enrolments/" + enrolmentId + "/progress", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/fundo/progress")
    public ResponseEntity<Map<String, Object>> recordProgress(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("progress", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/progress")
    public ResponseEntity<Map<String, Object>> listProgress(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        JsonNode n = learningClient.getFundo("progress", Map.of("subjectType", subjectType, "subjectId", subjectId));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/fundo/lessons/{lessonId}/open")
    public ResponseEntity<Map<String, Object>> openLesson(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String lessonId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("lessons/" + lessonId + "/open", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/courses/{courseId}/assessments")
    public ResponseEntity<Map<String, Object>> listCourseAssessments(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String courseId) {
        JsonNode n = learningClient.getFundo("courses/" + courseId + "/assessments", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/fundo/assessments/{assessmentId}")
    public ResponseEntity<Map<String, Object>> getAssessment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assessmentId) {
        JsonNode n = learningClient.getFundo("assessments/" + assessmentId, Map.of());
        if (n == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", n));
    }

    @PostMapping("/fundo/assessments/{assessmentId}/attempts")
    public ResponseEntity<Map<String, Object>> submitAssessmentAttempt(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assessmentId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("assessments/" + assessmentId + "/attempts", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/assessments/{assessmentId}/attempts")
    public ResponseEntity<Map<String, Object>> listAssessmentAttempts(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assessmentId,
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        JsonNode n = learningClient.getFundo("assessments/" + assessmentId + "/attempts", Map.of(
                "subjectType", subjectType,
                "subjectId", subjectId));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/fundo/attempts/{attemptId}")
    public ResponseEntity<Map<String, Object>> getAttempt(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String attemptId) {
        JsonNode n = learningClient.getFundo("attempts/" + attemptId, Map.of());
        if (n == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", n));
    }

    @GetMapping("/fundo/assessments/{assessmentId}/pending-reviews")
    public ResponseEntity<Map<String, Object>> pendingAssessmentReviews(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assessmentId,
            @RequestParam(required = false) Integer limit) {
        JsonNode n = learningClient.getFundo("assessments/" + assessmentId + "/pending-reviews", qp("limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/fundo/attempts/{attemptId}/manual-review")
    public ResponseEntity<Map<String, Object>> recordManualAssessmentReview(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String attemptId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("attempts/" + attemptId + "/manual-review", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/certificates")
    public ResponseEntity<Map<String, Object>> listCertificates(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        JsonNode n = learningClient.getFundo("certificates", Map.of("subjectType", subjectType, "subjectId", subjectId));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/fundo/certificates/{certificateId}")
    public ResponseEntity<Map<String, Object>> getCertificate(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String certificateId) {
        JsonNode n = learningClient.getFundo("certificates/" + certificateId, Map.of());
        if (n == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("data", n));
    }

    @PostMapping("/fundo/enrolments/{enrolmentId}/certificate")
    public ResponseEntity<Map<String, Object>> issueCertificate(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String enrolmentId) {
        JsonNode n = learningClient.postFundo("enrolments/" + enrolmentId + "/certificate", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/cpd/evidence")
    public ResponseEntity<Map<String, Object>> cpdEvidence(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        JsonNode n = learningClient.getFundo("cpd/evidence", Map.of("subjectType", subjectType, "subjectId", subjectId));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/cpd/eligible-completions")
    public ResponseEntity<Map<String, Object>> cpdEligibleCompletions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String subjectType,
            @RequestParam String subjectId) {
        JsonNode n = learningClient.getFundo("cpd/eligible-completions", Map.of("subjectType", subjectType, "subjectId", subjectId));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/fundo/reports/overview")
    public ResponseEntity<Map<String, Object>> reportOverview(@RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        JsonNode n = learningClient.getFundo("reports/overview", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/reports/cohort-completions")
    public ResponseEntity<Map<String, Object>> reportCohortCompletions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String pathwayId,
            @RequestParam(required = false) String courseId) {
        JsonNode n = learningClient.getFundo("reports/cohort-completions", qp("pathwayId", pathwayId, "courseId", courseId));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/fundo/reports/course-completions")
    public ResponseEntity<Map<String, Object>> reportCourseCompletions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {
        JsonNode n = learningClient.getFundo("reports/course-completions",
                qp("courseId", courseId, "subjectType", subjectType, "status", status, "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/fundo/reports/overdue-learning")
    public ResponseEntity<Map<String, Object>> reportOverdueLearning(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer limit) {
        JsonNode n = learningClient.getFundo("reports/overdue-learning",
                qp("courseId", courseId, "subjectType", subjectType, "status", status, "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @GetMapping("/fundo/reports/assessment-performance")
    public ResponseEntity<Map<String, Object>> reportAssessmentPerformance(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) Integer limit) {
        JsonNode n = learningClient.getFundo("reports/assessment-performance",
                qp("courseId", courseId, "subjectType", subjectType, "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/fundo/catalog")
    public ResponseEntity<Map<String, Object>> createCourse(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("catalog", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PutMapping("/fundo/catalog/{courseId}")
    public ResponseEntity<Map<String, Object>> updateCourse(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String courseId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.putFundo("catalog/" + courseId, body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/fundo/courses/{courseId}/modules")
    public ResponseEntity<Map<String, Object>> createModule(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String courseId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("courses/" + courseId + "/modules", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PutMapping("/fundo/modules/{moduleId}")
    public ResponseEntity<Map<String, Object>> updateModule(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String moduleId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.putFundo("modules/" + moduleId, body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/fundo/modules/{moduleId}/lessons")
    public ResponseEntity<Map<String, Object>> createLesson(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String moduleId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("modules/" + moduleId + "/lessons", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PutMapping("/fundo/lessons/{lessonId}")
    public ResponseEntity<Map<String, Object>> updateLesson(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String lessonId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.putFundo("lessons/" + lessonId, body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/fundo/pathways")
    public ResponseEntity<Map<String, Object>> createPathway(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("pathways", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PutMapping("/fundo/pathways/{pathwayId}")
    public ResponseEntity<Map<String, Object>> updatePathway(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String pathwayId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.putFundo("pathways/" + pathwayId, body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/fundo/pathways/{pathwayId}/items")
    public ResponseEntity<Map<String, Object>> addPathwayItem(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String pathwayId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("pathways/" + pathwayId + "/items", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/fundo/assessments")
    public ResponseEntity<Map<String, Object>> createAssessment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("assessments", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PutMapping("/fundo/assessments/{assessmentId}")
    public ResponseEntity<Map<String, Object>> updateAssessment(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assessmentId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.putFundo("assessments/" + assessmentId, body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/fundo/assessments/{assessmentId}/questions")
    public ResponseEntity<Map<String, Object>> addAssessmentQuestion(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assessmentId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("assessments/" + assessmentId + "/questions", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PutMapping("/fundo/assessments/{assessmentId}/questions/{questionId}")
    public ResponseEntity<Map<String, Object>> updateAssessmentQuestion(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String assessmentId,
            @PathVariable String questionId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.putFundo("assessments/" + assessmentId + "/questions/" + questionId, body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/subjects/{subjectType}/{subjectId}/record")
    public ResponseEntity<Map<String, Object>> record(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String subjectType,
            @PathVariable String subjectId) {
        JsonNode n = learningClient.getFundo("subjects/" + subjectType + "/" + subjectId + "/record", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/studio/dashboard")
    public ResponseEntity<Map<String, Object>> studioDashboard(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        JsonNode n = learningClient.getFundo("studio/dashboard", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/studio/courses/{courseId}/readiness")
    public ResponseEntity<Map<String, Object>> studioReadiness(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String courseId) {
        JsonNode n = learningClient.getFundo("studio/courses/" + courseId + "/readiness", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/fundo/ai/generate")
    public ResponseEntity<Map<String, Object>> aiGenerate(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("ai/generate", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/fundo/nompilo/assist")
    public ResponseEntity<Map<String, Object>> nompiloAssist(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = tryStructuredNompiloAdapter(tenantId, actorId, body);
        if (n == null) {
            n = learningClient.postFundo("nompilo/assist", body);
        }
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().put("mode", "stub")));
    }

    @GetMapping("/fundo/library/resources")
    public ResponseEntity<Map<String, Object>> listLibraryResources(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(defaultValue = "50") int limit) {
        JsonNode n = learningClient.getFundo("library/resources", Map.of("limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/fundo/library/resources")
    public ResponseEntity<Map<String, Object>> createLibraryResource(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("library/resources", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/fundo/library/resources/{resourceId}/links")
    public ResponseEntity<Map<String, Object>> linkLibraryResource(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String resourceId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("library/resources/" + resourceId + "/links", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/fundo/library/uploads")
    public ResponseEntity<Map<String, Object>> uploadLibraryResource(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("library/uploads", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/media/assets")
    public ResponseEntity<Map<String, Object>> listMediaAssets(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(defaultValue = "50") int limit) {
        JsonNode n = learningClient.getFundo("media/assets", Map.of("limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/fundo/media/assets")
    public ResponseEntity<Map<String, Object>> createMediaAsset(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("media/assets", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/notifications")
    public ResponseEntity<Map<String, Object>> listLearningNotifications(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam String subjectType,
            @RequestParam String subjectId,
            @RequestParam(defaultValue = "50") int limit) {
        JsonNode n = learningClient.getFundo("notifications", Map.of(
                "subjectType", subjectType,
                "subjectId", subjectId,
                "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/fundo/notifications")
    public ResponseEntity<Map<String, Object>> scheduleLearningNotification(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("notifications", body);
        JsonNode commsDispatch = dispatchToCommsHub(body);
        ObjectNode out = JsonNodeFactory.instance.objectNode();
        out.set("learning", n != null ? n : JsonNodeFactory.instance.objectNode());
        out.set("commsHubDispatch", commsDispatch != null ? commsDispatch : JsonNodeFactory.instance.objectNode().put("status", "not-dispatched"));
        return ResponseEntity.ok(Map.of("data", out));
    }

    @GetMapping("/fundo/interactive/activities")
    public ResponseEntity<Map<String, Object>> listInteractiveActivities(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String lessonId,
            @RequestParam(defaultValue = "50") int limit) {
        JsonNode n = learningClient.getFundo("interactive/activities", qp(
                "courseId", courseId,
                "lessonId", lessonId,
                "limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/fundo/interactive/activities")
    public ResponseEntity<Map<String, Object>> createInteractiveActivity(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("interactive/activities", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/interactive/activities/{activityId}/responses")
    public ResponseEntity<Map<String, Object>> listInteractiveResponses(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String activityId,
            @RequestParam(defaultValue = "50") int limit) {
        JsonNode n = learningClient.getFundo("interactive/activities/" + activityId + "/responses", Map.of("limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/fundo/interactive/activities/{activityId}/responses")
    public ResponseEntity<Map<String, Object>> submitInteractiveResponse(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String activityId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("interactive/activities/" + activityId + "/responses", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/cohorts")
    public ResponseEntity<Map<String, Object>> listCohorts(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(defaultValue = "50") int limit) {
        JsonNode n = learningClient.getFundo("cohorts", Map.of("limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/fundo/cohorts")
    public ResponseEntity<Map<String, Object>> createCohort(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("cohorts", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @PostMapping("/fundo/cohorts/{cohortId}/members")
    public ResponseEntity<Map<String, Object>> addCohortMember(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String cohortId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("cohorts/" + cohortId + "/members", body);
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode()));
    }

    @GetMapping("/fundo/sessions")
    public ResponseEntity<Map<String, Object>> listSessions(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestParam(defaultValue = "50") int limit) {
        JsonNode n = learningClient.getFundo("sessions", Map.of("limit", limit));
        return ResponseEntity.ok(Map.of("data", n != null ? n : JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode())));
    }

    @PostMapping("/fundo/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.postFundo("sessions", body);
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
