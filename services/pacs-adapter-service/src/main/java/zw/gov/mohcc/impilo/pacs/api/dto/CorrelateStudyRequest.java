package zw.gov.mohcc.impilo.pacs.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

/**
 * Links an imaging study to an OROS order so downstream consumers can match results.
 */
public class CorrelateStudyRequest {

    @NotBlank
    @JsonAlias("oros_order_id")
    private String orosOrderId;

    public String getOrosOrderId() {
        return orosOrderId;
    }

    public void setOrosOrderId(String orosOrderId) {
        this.orosOrderId = orosOrderId;
    }
}
