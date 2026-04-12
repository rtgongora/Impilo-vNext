package zw.gov.mohcc.impilo.pacs.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Links an imaging study to an OROS order so downstream consumers can match results.
 */
public class CorrelateStudyRequest {

    @NotBlank
    private String orosOrderId;

    public String getOrosOrderId() {
        return orosOrderId;
    }

    public void setOrosOrderId(String orosOrderId) {
        this.orosOrderId = orosOrderId;
    }
}
