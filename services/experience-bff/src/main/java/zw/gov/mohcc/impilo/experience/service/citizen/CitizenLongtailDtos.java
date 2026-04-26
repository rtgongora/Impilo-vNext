package zw.gov.mohcc.impilo.experience.service.citizen;

/**
 * Request bodies for citizen long-tail BFF endpoints (mobile contract).
 */
public final class CitizenLongtailDtos {

    private CitizenLongtailDtos() {}

    public record IssueShareCodeRequest(String purpose) {}

    public record ClaimShareRequest(String code) {}

    public record VerifyCredentialRequest(String payload) {}

    public record IssueDelegatedPickupRequest(String delegate_name, String delegate_phone) {}
}
