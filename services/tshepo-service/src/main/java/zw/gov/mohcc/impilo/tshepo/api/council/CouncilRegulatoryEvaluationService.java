package zw.gov.mohcc.impilo.tshepo.api.council;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Stub council regulatory policy plane — extend with PDP/ABAC rules and council role bindings.
 */
@Service
public class CouncilRegulatoryEvaluationService {

    private static final Set<String> REGISTERED_ACTIONS = Set.of(
            "CREATE_OBLIGATION",
            "INITIATE_MUSHEX_INTENT",
            "UPSERT_COUNCIL_PROFILE",
            "UPSERT_COUNCIL_REGULATORY_CONFIG",
            "UPSERT_FUNDO_LINK",
            "RECORD_COUNCIL_REVIEW",
            "ACCEPT_FUNDO_CPD",
            "REJECT_FUNDO_CPD",
            "RECORD_LEARNING_COMPLETION",
            "OPEN_LEARNING_RESOURCE",
            "ASSIGN_LEARNING_PATH",
            "REGISTER_LEARNING_PREREQUISITE");

    private final boolean denyByDefault;

    public CouncilRegulatoryEvaluationService(
            @Value("${tshepo.council-regulatory.deny-by-default:true}") boolean denyByDefault) {
        this.denyByDefault = denyByDefault;
    }

    public CouncilRegulatoryEvaluateResponse evaluate(CouncilRegulatoryEvaluateRequest request) {
        String action = request.getWorkflowAction();
        if (action == null || action.isBlank()) {
            return CouncilRegulatoryEvaluateResponse.deny("MISSING_WORKFLOW_ACTION");
        }
        if (denyByDefault && !REGISTERED_ACTIONS.contains(action)) {
            return CouncilRegulatoryEvaluateResponse.deny("UNKNOWN_ACTION");
        }
        return CouncilRegulatoryEvaluateResponse.permit("ALLOW");
    }
}
