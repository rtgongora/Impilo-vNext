package zw.gov.mohcc.impilo.varapi.api.dto;

public record CreateCouncilRequest(
        String councilCode,
        String name,
        String councilType,
        String description,
        String website,
        String email,
        String phone
) {}
