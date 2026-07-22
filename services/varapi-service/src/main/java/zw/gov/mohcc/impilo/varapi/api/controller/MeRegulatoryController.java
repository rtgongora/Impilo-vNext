package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.core.MyRegulatoryService;
import zw.gov.mohcc.impilo.varapi.core.MyRegulatoryService.MyRegulatorySummary;

/**
 * "My Regulatory Affairs" (ROM-W3) — the signed-in person's OWN regulatory standing. Keyed on the
 * Health-ID from the trust context (never a providerId query param), so it is genuinely
 * self-service and cannot be used to read another person's regulatory record.
 */
@RestController
@RequestMapping("/v1/me/regulatory")
public class MeRegulatoryController {

    private final MyRegulatoryService myRegulatoryService;

    public MeRegulatoryController(MyRegulatoryService myRegulatoryService) {
        this.myRegulatoryService = myRegulatoryService;
    }

    @GetMapping("/summary")
    public MyRegulatorySummary summary() {
        TrustContext ctx = TrustContextHolder.require();
        return myRegulatoryService.summaryForPerson(ctx.tenantId(), ctx.actorId());
    }
}
