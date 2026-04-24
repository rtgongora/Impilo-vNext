package zw.gov.mohcc.impilo.tshepo.authz.dto;

import java.util.UUID;

/** Active workflow escalation grant bound to an ext_authz request. */
public record EscalationGrantView(
        UUID grantToken,
        String visibilityCeiling,
        String workflowType
) {}
