package zw.gov.mohcc.impilo.campaigns.api;

import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.campaigns.core.CampaignService;
import zw.gov.mohcc.impilo.campaigns.core.DispatchService;
import zw.gov.mohcc.impilo.campaigns.core.EnrollmentService;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.CampaignEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.EnrollmentEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

@RestController
@RequestMapping("/internal/v1/campaigns")
public class CampaignController {

    private final CampaignService campaignService;
    private final EnrollmentService enrollmentService;
    private final DispatchService dispatchService;

    public CampaignController(CampaignService campaignService,
                               EnrollmentService enrollmentService,
                               DispatchService dispatchService) {
        this.campaignService = campaignService;
        this.enrollmentService = enrollmentService;
        this.dispatchService = dispatchService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CampaignEntity>> createCampaign(
            @RequestBody CreateCampaignRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        CampaignEntity campaign = campaignService.createCampaign(
                ctx.tenantId(), ctx.actorId(), ctx.correlationId(),
                request.name(), request.description(),
                request.campaignType(), request.targetGroup(),
                request.messageTemplate(), request.channel());

        return ResponseEntity.status(201).body(
                ApiResponse.ok(campaign, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CampaignEntity>>> listCampaigns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        TrustContext ctx = TrustContextHolder.require();

        Page<CampaignEntity> results = campaignService.listCampaigns(
                ctx.tenantId(), PageRequest.of(page, size));

        return ResponseEntity.ok(ApiResponse.ok(
                PagedResponse.of(results.getContent(), page, size, results.getTotalElements()),
                ctx.correlationId().toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CampaignEntity>> getCampaign(@PathVariable Long id) {
        TrustContext ctx = TrustContextHolder.require();
        CampaignEntity campaign = campaignService.findById(id, ctx.tenantId());
        return ResponseEntity.ok(ApiResponse.ok(campaign, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<CampaignEntity>> closeCampaign(@PathVariable Long id) {
        TrustContext ctx = TrustContextHolder.require();
        CampaignEntity campaign = campaignService.completeCampaign(
                id, ctx.tenantId(), ctx.actorId(), ctx.correlationId());
        return ResponseEntity.ok(ApiResponse.ok(campaign, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/enroll")
    public ResponseEntity<ApiResponse<EnrollmentEntity>> enroll(
            @PathVariable Long id,
            @RequestBody EnrollRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        EnrollmentEntity enrollment = enrollmentService.enroll(
                id, ctx.tenantId(), ctx.actorId(), ctx.correlationId(),
                request.participantId(), request.participantType());

        return ResponseEntity.status(201).body(
                ApiResponse.ok(enrollment, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/dispatch")
    public ResponseEntity<ApiResponse<DispatchService.DispatchResult>> dispatch(
            @PathVariable Long id) {
        TrustContext ctx = TrustContextHolder.require();

        DispatchService.DispatchResult result = dispatchService.dispatch(
                id, ctx.tenantId(), ctx.actorId(), ctx.correlationId());

        return ResponseEntity.ok(
                ApiResponse.ok(result, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/live-broadcast")
    public ResponseEntity<ApiResponse<Map<String, Object>>> scheduleLiveBroadcast(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> result = campaignService.scheduleLiveBroadcast(
                id, ctx.tenantId(), ctx.actorId(), ctx.correlationId(), request);
        return ResponseEntity.status(201).body(
                ApiResponse.ok(result, ctx.correlationId().toString()));
    }

    public record CreateCampaignRequest(
            String name,
            String description,
            String campaignType,
            String targetGroup,
            String messageTemplate,
            String channel
    ) {}

    public record EnrollRequest(
            String participantId,
            String participantType
    ) {}
}
