package zw.gov.mohcc.impilo.devportal.api.dto;

import java.util.List;

public record IssueKeyRequest(
        String label,
        List<String> scopes,
        Integer expiresInDays
) {}
