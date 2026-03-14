package zw.gov.mohcc.impilo.support.api.dto;

public record UpdateTicketRequest(
        String title,
        String description,
        String category,
        String priority,
        String status,
        String assigneeRef,
        String resolution) {}
