package zw.gov.mohcc.impilo.pacs.api.dto;

import jakarta.validation.constraints.NotBlank;

public class OrderLinkRequest {

    @NotBlank
    private String orderRef;

    private String orderingProviderRef;

    private String status = "LINKED";

    public String getOrderRef() {
        return orderRef;
    }

    public void setOrderRef(String orderRef) {
        this.orderRef = orderRef;
    }

    public String getOrderingProviderRef() {
        return orderingProviderRef;
    }

    public void setOrderingProviderRef(String orderingProviderRef) {
        this.orderingProviderRef = orderingProviderRef;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
