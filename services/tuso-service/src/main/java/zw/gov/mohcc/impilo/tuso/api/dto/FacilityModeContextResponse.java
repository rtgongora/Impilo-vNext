package zw.gov.mohcc.impilo.tuso.api.dto;

import java.util.List;

/**
 * The C4 {@code FacilityModeContext} read-model produced by TUSO (facility SoR).
 *
 * <p>This is the frozen boundary between the T1 provider lane (which ENTERS facility
 * mode and reads this) and the T4 facility lane (which BUILDS facility mode and
 * produces this). Single producer. Tenant-scoped; {@code visibility=AGGREGATE_ONLY}
 * hides row-level detail under cross-tenant guard.</p>
 */
public record FacilityModeContextResponse(
        FacilityNode facility,
        SetupState setupState,
        Ops ops,
        String visibility // ROW_DETAIL | AGGREGATE_ONLY
) {

    public record FacilityNode(
            Long facilityId,
            String tenantId,
            String code,
            String name,
            String type,
            String regulatoryStatus,
            List<DepartmentRef> departments,
            List<ServicePointRef> servicePoints
    ) {}

    public record DepartmentRef(
            Long departmentId,
            String name,
            String serviceLine
    ) {}

    public record ServicePointRef(
            String servicePointId,
            Long departmentId,
            String name,
            String queueId
    ) {}

    public record SetupState(
            boolean departmentsConfigured,
            boolean servicePointsConfigured,
            boolean queuesConfigured,
            boolean workflowsConfigured,
            boolean workforceLinked,
            boolean orosRoutingConfigured,
            boolean khulumaChannelsConfigured,
            boolean fundoReady,
            boolean goLive
    ) {}

    public record Ops(
            int openEncounters,
            int queueDepth,
            Integer bedOccupancy
    ) {}
}
