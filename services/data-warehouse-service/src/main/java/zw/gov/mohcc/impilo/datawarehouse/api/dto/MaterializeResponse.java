package zw.gov.mohcc.impilo.datawarehouse.api.dto;

public record MaterializeResponse(String eventId, int upsertedCount, String status) {}
