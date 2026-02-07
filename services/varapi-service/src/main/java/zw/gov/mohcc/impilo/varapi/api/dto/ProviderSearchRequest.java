package zw.gov.mohcc.impilo.varapi.api.dto;

public record ProviderSearchRequest(
        String query,
        String profession,
        String status,
        int page,
        int size
) {
    public ProviderSearchRequest {
        if (page < 0) page = 0;
        if (size <= 0) size = 20;
    }

    public ProviderSearchRequest(String query, String profession, String status) {
        this(query, profession, status, 0, 20);
    }
}
