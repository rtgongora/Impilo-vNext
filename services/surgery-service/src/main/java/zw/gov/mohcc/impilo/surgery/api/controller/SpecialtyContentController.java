package zw.gov.mohcc.impilo.surgery.api.controller;

import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.surgery.core.SpecialtyContentService;

import java.util.List;
import java.util.Map;

/** SB-2/SB-4 — specialty content read API. Reachable via BFF + tshepo-authz V305. */
@RestController
@RequestMapping("/internal/v1/surgery/specialties")
public class SpecialtyContentController {

    private final SpecialtyContentService service;

    public SpecialtyContentController(SpecialtyContentService service) {
        this.service = service;
    }

    @GetMapping("/indications")
    public List<Map<String, Object>> indications(@RequestParam String specialty) {
        return service.indicationsFor(specialty);
    }

    @GetMapping("/templates")
    public List<Map<String, Object>> templates(@RequestParam String specialty) {
        return service.templatesFor(specialty);
    }
}
