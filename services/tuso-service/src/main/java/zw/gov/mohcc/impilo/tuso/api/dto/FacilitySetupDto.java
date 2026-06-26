package zw.gov.mohcc.impilo.tuso.api.dto;

/** Setup-wizard read/write DTOs. */
public final class FacilitySetupDto {

    private FacilitySetupDto() {}

    public record StepUpdateRequest(
            String step,      // DEPARTMENTS|SERVICE_POINTS|QUEUES|WORKFLOWS|WORKFORCE|OROS_ROUTING|KHULUMA_CHANNELS|FUNDO_READINESS|GO_LIVE
            boolean complete
    ) {}

    public record StateResponse(
            Long facilityId,
            boolean departmentsConfigured,
            boolean servicePointsConfigured,
            boolean queuesConfigured,
            boolean workflowsConfigured,
            boolean workforceLinked,
            boolean orosRoutingConfigured,
            boolean khulumaChannelsConfigured,
            boolean fundoReady,
            boolean goLive,
            String nextStep,            // null when ready / live
            boolean readyForGoLive
    ) {}
}
