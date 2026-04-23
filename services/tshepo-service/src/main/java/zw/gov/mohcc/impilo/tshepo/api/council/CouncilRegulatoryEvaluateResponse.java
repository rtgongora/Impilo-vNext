package zw.gov.mohcc.impilo.tshepo.api.council;

public class CouncilRegulatoryEvaluateResponse {

    private boolean allowed;
    private String outcome;

    public static CouncilRegulatoryEvaluateResponse permit(String outcome) {
        CouncilRegulatoryEvaluateResponse r = new CouncilRegulatoryEvaluateResponse();
        r.setAllowed(true);
        r.setOutcome(outcome);
        return r;
    }

    public static CouncilRegulatoryEvaluateResponse deny(String outcome) {
        CouncilRegulatoryEvaluateResponse r = new CouncilRegulatoryEvaluateResponse();
        r.setAllowed(false);
        r.setOutcome(outcome);
        return r;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }
}
