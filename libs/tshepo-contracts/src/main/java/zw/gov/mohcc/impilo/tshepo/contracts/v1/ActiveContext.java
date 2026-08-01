package zw.gov.mohcc.impilo.tshepo.contracts.v1;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Selected work context — selecting a workplace does not create authority. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActiveContext(
        String contractVersion,
        String tenantId,
        String facilityId,
        String councilId,
        String departmentId,
        String wardId,
        String workspaceId,
        String programmeId,
        String shiftId,
        String workContextTokenId
) {
    public ActiveContext {
        TrustContractVersion.requireV1(contractVersion);
    }

    public static ActiveContext of(
            String tenantId,
            String facilityId,
            String councilId,
            String departmentId,
            String wardId,
            String workspaceId,
            String programmeId,
            String shiftId,
            String workContextTokenId) {
        return new ActiveContext(
                TrustContractVersion.V1,
                tenantId,
                facilityId,
                councilId,
                departmentId,
                wardId,
                workspaceId,
                programmeId,
                shiftId,
                workContextTokenId);
    }
}
