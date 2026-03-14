package zw.gov.mohcc.impilo.datagovernance.api.dto;

public record ExportRequest(String dataset, String purposeOfUse, String format, String filters) {}
