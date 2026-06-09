package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/internal/v1/learning")
public class LearningController {

    private final LearningServiceClient learningClient;

    public LearningController(LearningServiceClient learningClient) {
        this.learningClient = learningClient;
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

    @GetMapping("/v11/metadata/languages")
    public ResponseEntity<Map<String, Object>> languageMetadata(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId) {
        JsonNode n = learningClient.getV11("metadata/languages", Map.of());
        return ResponseEntity.ok(Map.of("data", n != null ? n : emptyItems()));
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
        if (n == null) {
            return upstreamUnavailable("LEARNING_COURSE_CREATE_FAILED", "Learning service did not create the course");
        }
        return ResponseEntity.ok(Map.of("data", n));
    }

    @PutMapping("/v11/catalog/{courseId}")
    public ResponseEntity<Map<String, Object>> updateCourse(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @PathVariable String courseId,
            @RequestBody Map<String, Object> body) {
        JsonNode n = learningClient.putV11("catalog/" + courseId, body);
        if (n == null) {
            return upstreamUnavailable("LEARNING_COURSE_UPDATE_FAILED", "Learning service did not update the course");
        }
        return ResponseEntity.ok(Map.of("data", n));
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

    private static JsonNode emptyItems() {
        return JsonNodeFactory.instance.objectNode().set("items", JsonNodeFactory.instance.arrayNode());
    }

    private static ResponseEntity<Map<String, Object>> upstreamUnavailable(String code, String message) {
        return ResponseEntity.status(502).body(Map.of("error", Map.of(
                "code", code,
                "message", message)));
    }
}
