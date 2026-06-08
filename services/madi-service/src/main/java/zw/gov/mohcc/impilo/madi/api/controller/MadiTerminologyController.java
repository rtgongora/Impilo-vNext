package zw.gov.mohcc.impilo.madi.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.madi.core.MadiZiboPinService;

import java.util.Map;

@RestController
@RequestMapping("/internal/v1/madi/terminology")
public class MadiTerminologyController {

    private final MadiZiboPinService ziboPinService;

    public MadiTerminologyController(MadiZiboPinService ziboPinService) {
        this.ziboPinService = ziboPinService;
    }

    @GetMapping("/component-pins")
    public Map<String, Object> componentPins(
            @RequestParam(value = "jurisdiction", required = false, defaultValue = "ZW") String jurisdiction) {
        return ziboPinService.componentPins(jurisdiction);
    }
}
