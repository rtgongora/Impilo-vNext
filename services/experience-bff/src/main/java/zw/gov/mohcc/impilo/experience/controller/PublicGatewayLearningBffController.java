package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.LearningServiceClient;

/**
 * Public learning lane for the gateway (no authentication). Browse the PUBLISHED course
 * catalogue (citizen health courses, caregiver education, first aid, digital-health literacy)
 * WITHOUT signing in — catalogue facts only. Enrolment, assessed learning, certificates,
 * CPD credits and progress tracking require sign-in.
 *
 * <ul>
 *   <li>GET /learning/catalog?category=&amp;level=&amp;language=&amp;limit= — course catalogue (learning)</li>
 *   <li>GET /learning/catalog/{code}                              — one course (learning)</li>
 * </ul>
 */
@RestController
@RequestMapping("/internal/v1/public/gateway/learning")
public class PublicGatewayLearningBffController {

    private static final Logger log = LoggerFactory.getLogger(PublicGatewayLearningBffController.class);

    private final LearningServiceClient learningClient;

    public PublicGatewayLearningBffController(LearningServiceClient learningClient) {
        this.learningClient = learningClient;
    }

    @GetMapping("/catalog")
    public ResponseEntity<JsonNode> catalog(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String language,
            @RequestParam(required = false, defaultValue = "25") int limit) {
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            if (category != null && !category.isBlank()) params.add("category", category);
            if (level != null && !level.isBlank()) params.add("level", level);
            if (language != null && !language.isBlank()) params.add("language", language);
            params.add("limit", String.valueOf(limit));
            return ResponseEntity.ok(learningClient.publicCourseCatalog(params));
        } catch (Exception e) {
            log.warn("Public gateway learning catalog failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @GetMapping("/catalog/{code}")
    public ResponseEntity<JsonNode> course(@PathVariable String code) {
        try {
            return ResponseEntity.ok(learningClient.publicCourse(code));
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound nf) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.warn("Public gateway learning course failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
