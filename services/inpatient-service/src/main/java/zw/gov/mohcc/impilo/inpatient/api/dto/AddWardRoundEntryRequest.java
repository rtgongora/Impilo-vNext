package zw.gov.mohcc.impilo.inpatient.api.dto;

public record AddWardRoundEntryRequest(
        String assessment,
        String plan,
        String newOrders,
        String escalation,
        String reviewedBy
) {}
