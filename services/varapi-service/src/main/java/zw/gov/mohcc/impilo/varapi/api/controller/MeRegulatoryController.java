package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.core.MyRegulatoryService;
import zw.gov.mohcc.impilo.varapi.core.MyRegulatoryService.MyRegulatorySummary;
import zw.gov.mohcc.impilo.varapi.core.RenewalEligibilityService;
import zw.gov.mohcc.impilo.varapi.core.RenewalEligibilityService.RenewalEligibility;

/**
 * "My Regulatory Affairs" (ROM-W3) — the signed-in person's OWN regulatory standing. Keyed on the
 * Health-ID from the trust context (never a providerId query param), so it is genuinely
 * self-service and cannot be used to read another person's regulatory record.
 */
@RestController
@RequestMapping("/v1/me/regulatory")
public class MeRegulatoryController {

    private final MyRegulatoryService myRegulatoryService;
    private final RenewalEligibilityService renewalEligibilityService;

    public MeRegulatoryController(MyRegulatoryService myRegulatoryService,
                                  RenewalEligibilityService renewalEligibilityService) {
        this.myRegulatoryService = myRegulatoryService;
        this.renewalEligibilityService = renewalEligibilityService;
    }

    @GetMapping("/summary")
    public MyRegulatorySummary summary() {
        TrustContext ctx = TrustContextHolder.require();
        return myRegulatoryService.summaryForPerson(ctx.tenantId(), ctx.actorId());
    }

    /** Can I renew? (ROM-W5) — CPD-gated eligibility for the signed-in professional. */
    @GetMapping("/renewal-eligibility")
    public RenewalEligibility renewalEligibility() {
        TrustContext ctx = TrustContextHolder.require();
        return renewalEligibilityService.evaluateForPerson(ctx.tenantId(), ctx.actorId());
    }

    /** My applications (ROM-U) — the signed-in person's own regulatory applications. */
    @GetMapping("/applications")
    public java.util.List<MyRegulatoryService.MyApplication> myApplications() {
        TrustContext ctx = TrustContextHolder.require();
        return myRegulatoryService.applicationsForPerson(ctx.tenantId(), ctx.actorId());
    }

    /** Complaints involving me (ROM-U3) — disciplinary matters where I am the respondent. */
    @GetMapping("/complaints")
    public java.util.List<MyRegulatoryService.MyComplaint> myComplaints() {
        TrustContext ctx = TrustContextHolder.require();
        return myRegulatoryService.complaintsForPerson(ctx.tenantId(), ctx.actorId());
    }
}
