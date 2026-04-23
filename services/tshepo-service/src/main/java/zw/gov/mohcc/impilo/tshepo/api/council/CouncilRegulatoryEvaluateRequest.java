package zw.gov.mohcc.impilo.tshepo.api.council;

/**
 * Request body for council regulatory workflow checks (Varapi and other registry services).
 */
public class CouncilRegulatoryEvaluateRequest {

    private String workflowAction;
    private Long councilId;
    private Long applicationId;
    private Long providerId;
    private String actorId;

    public String getWorkflowAction() {
        return workflowAction;
    }

    public void setWorkflowAction(String workflowAction) {
        this.workflowAction = workflowAction;
    }

    public Long getCouncilId() {
        return councilId;
    }

    public void setCouncilId(Long councilId) {
        this.councilId = councilId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }
}
