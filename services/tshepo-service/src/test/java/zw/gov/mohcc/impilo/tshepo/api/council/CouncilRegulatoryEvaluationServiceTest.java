package zw.gov.mohcc.impilo.tshepo.api.council;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CouncilRegulatoryEvaluationServiceTest {

    @Test
    void denyByDefault_blocksUnknownAction() {
        CouncilRegulatoryEvaluationService svc = new CouncilRegulatoryEvaluationService(true);
        CouncilRegulatoryEvaluateRequest req = new CouncilRegulatoryEvaluateRequest();
        req.setWorkflowAction("MYSTERY_ACTION");
        assertFalse(svc.evaluate(req).isAllowed());
    }

    @Test
    void denyByDefault_allowsRegisteredAction() {
        CouncilRegulatoryEvaluationService svc = new CouncilRegulatoryEvaluationService(true);
        CouncilRegulatoryEvaluateRequest req = new CouncilRegulatoryEvaluateRequest();
        req.setWorkflowAction("CREATE_OBLIGATION");
        assertTrue(svc.evaluate(req).isAllowed());
    }

    @Test
    void permissiveMode_allowsUnknownAction() {
        CouncilRegulatoryEvaluationService svc = new CouncilRegulatoryEvaluationService(false);
        CouncilRegulatoryEvaluateRequest req = new CouncilRegulatoryEvaluateRequest();
        req.setWorkflowAction("MYSTERY_ACTION");
        assertTrue(svc.evaluate(req).isAllowed());
    }
}
