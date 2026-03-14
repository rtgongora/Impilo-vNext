package zw.gov.mohcc.impilo.datawarehouse.api.dto;

public record MaterializeRequest(String eventId, String eventType, String envelopeJson) {}
