package zw.gov.mohcc.impilo.credential.api.dto;

public record CredentialSearchRequest(
        String subjectType,
        String subjectId,
        String credentialType,
        String status,
        Integer page,
        Integer size
) {
    public int resolvedPage() {
        return page != null && page >= 0 ? page : 0;
    }

    public int resolvedSize() {
        return size != null && size > 0 && size <= 100 ? size : 20;
    }
}
