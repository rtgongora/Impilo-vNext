package zw.gov.mohcc.impilo.datagovernance.api.dto;

public record ExportResponse(String decision, String dataset, String purposeOfUse,
                               String exportId, String message) {}
