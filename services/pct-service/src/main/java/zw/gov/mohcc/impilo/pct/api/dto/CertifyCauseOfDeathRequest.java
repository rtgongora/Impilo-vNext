package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * WHO cause-of-death certification draft / sign request (WS#8). The certifier id and role come
 * from the trust context; the licence number must be supplied and the role must be eligible —
 * an ineligible certifier is hard-blocked. {@code sign=true} promotes a DRAFT to SIGNED.
 */
public record CertifyCauseOfDeathRequest(
        @NotBlank String immediateCause,
        String antecedentCause,
        String underlyingCause,
        String contributoryCause,
        String manner,
        String externalCause,
        String certifierLicence,
        boolean sign
) {}
