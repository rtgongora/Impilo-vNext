package zw.gov.mohcc.impilo.surv.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.surv.core.CaseService;
import zw.gov.mohcc.impilo.surv.core.CaseStatus;
import zw.gov.mohcc.impilo.surv.persistence.entity.CaseEntity;

@RestController
@RequestMapping("/internal/v1/cases")
public class CaseController {

    private static final Logger log = LoggerFactory.getLogger(CaseController.class);

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping
    public ResponseEntity<?> listCases(
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        RequestContext ctx = RequestContextHolder.require();

        log.info("List cases [status={}, page={}, size={}] correlationId={}",
                status, page, size, ctx.correlationId());

        Page<CaseEntity> results = caseService.listCases(
                UUID.fromString(ctx.tenantId()), status, PageRequest.of(page, size));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", results.getContent());
        body.put("page", page);
        body.put("size", size);
        body.put("total_elements", results.getTotalElements());
        body.put("has_more", results.hasNext());
        return ResponseEntity.ok(body);
    }
}
