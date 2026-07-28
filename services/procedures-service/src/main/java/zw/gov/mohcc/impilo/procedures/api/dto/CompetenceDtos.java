package zw.gov.mohcc.impilo.procedures.api.dto;

import java.util.List;

/** Shapes for competence and privilege resolution (pipeline §7). */
public final class CompetenceDtos {

    private CompetenceDtos() {
    }

    /** One privilege as VARAPI holds it. {@code supervisingProviderPublicId} is the field that matters. */
    public record PrivilegeRecord(String scope, String privilegeType, String status,
                                  String supervisingProviderPublicId) {
    }

    /**
     * The answer to "may this person perform this procedure, alone?".
     *
     * <p>{@code capacity} is INDEPENDENT, SUPERVISED, NOT_PERMITTED or UNKNOWN. UNKNOWN exists
     * because an unreachable credentialing service is not a credentialed operator, and a
     * resolver that cannot distinguish those two will eventually report the wrong one.</p>
     */
    public record CompetenceVerdict(String providerId,
                                    String definitionCode,
                                    String capacity,
                                    boolean mayProceedAlone,
                                    String supervisingProviderPublicId,
                                    boolean countersignatureRequired,
                                    List<String> reasons) {
    }
}
