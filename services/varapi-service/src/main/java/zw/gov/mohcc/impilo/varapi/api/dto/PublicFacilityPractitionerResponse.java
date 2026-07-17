package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.LocalDate;

/**
 * Allow-listed public disclosure for a practitioner who genuinely practises at a
 * facility (gateway public find-care lane —
 * docs/architecture/gateway-public-lane-security-adr.md).
 *
 * <p>Mirrors {@link PublicPractitionerVerificationResponse} plus the facility
 * {@code roleTitle}. Contains ONLY public register facts: display name,
 * profession/cadre, the role held at this facility, register status,
 * practising-certificate validity window and the registering authority label.
 * NEVER contact details, national identifiers, date of birth, internal
 * identifiers, tenant/health identifiers, affiliation notes, or any
 * availability / slot / schedule signal.</p>
 *
 * <p>A provider appears ONLY when registration + active affiliation + facility
 * practice context + platform authority are ALL confirmed; a provider failing
 * any axis is silently dropped (never a partial/pending row).</p>
 *
 * @param displayName             public register name (title + given + family)
 * @param profession             registered profession
 * @param cadre                  registered cadre
 * @param roleTitle              role held at this facility (affiliation role title / type label)
 * @param registrationNumber     the register's stored registration number
 * @param registerStatus         REGISTERED / SUSPENDED / … (council register status)
 * @param registeredSince        registration date on the register
 * @param registrationExpiryDate registration expiry (null = no recorded expiry)
 * @param licenceStatus          current practising-certificate/licence status
 * @param licenceValidFrom       practising-certificate validity window start
 * @param licenceValidTo         practising-certificate validity window end
 * @param registeringAuthority   registering authority label (council name)
 */
public record PublicFacilityPractitionerResponse(
        String displayName,
        String profession,
        String cadre,
        String roleTitle,
        String registrationNumber,
        String registerStatus,
        LocalDate registeredSince,
        LocalDate registrationExpiryDate,
        String licenceStatus,
        LocalDate licenceValidFrom,
        LocalDate licenceValidTo,
        String registeringAuthority
) {
}
