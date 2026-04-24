package zw.gov.mohcc.impilo.pacs.api.dto;

public class LaunchViewerRequest {

    private String viewerType = "DICOMWEB_STACK";

    /** Workflow anchor: e.g. encounter URL, order id, or EHR deep link */
    private String contextRef;

    /** Policy scope hint: FULL_DICOM, PREVIEW_ONLY, DEIDENTIFIED */
    private String scope;

    public String getViewerType() {
        return viewerType;
    }

    public void setViewerType(String viewerType) {
        this.viewerType = viewerType;
    }

    public String getContextRef() {
        return contextRef;
    }

    public void setContextRef(String contextRef) {
        this.contextRef = contextRef;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
