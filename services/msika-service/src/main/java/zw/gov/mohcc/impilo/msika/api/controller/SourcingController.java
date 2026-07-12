package zw.gov.mohcc.impilo.msika.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.msika.core.SourcingService;

import java.util.List;
import java.util.Map;

/**
 * Requirement-sourcing surface: HPA requirement codes → marketplace sourcing
 * categories (+ live published-listing counts). Batch resolve is a POST —
 * checklists carry hundreds of codes.
 */
@RestController
@RequestMapping("/v1/sourcing")
public class SourcingController {

    private final SourcingService sourcingService;

    public SourcingController(SourcingService sourcingService) {
        this.sourcingService = sourcingService;
    }

    public record ResolveRequest(List<String> codes) {}

    @GetMapping("/categories")
    public ResponseEntity<List<SourcingService.CategoryView>> categories() {
        return ResponseEntity.ok(sourcingService.listCategories());
    }

    @GetMapping("/categories/{code}")
    public ResponseEntity<SourcingService.CategoryView> category(@PathVariable String code) {
        return sourcingService.getCategory(code)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/resolve")
    public ResponseEntity<Map<String, SourcingService.ResolutionView>> resolve(
            @RequestBody ResolveRequest request) {
        return ResponseEntity.ok(sourcingService.resolve(request.codes()));
    }
}
