package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.NotBlank;

public record FacilityContactDto(
        @NotBlank(message = "Contact type is required")
        String contactType,

        String name,
        String phone,
        String email,
        String role,

        /**
         * HAR W6 — false for a number the regulator's legacy records asserted but nobody has
         * confirmed. The public lane shows these (a possibly-stale number beats no number when you
         * need to ask whether a clinic is open) but must say which they are, so a citizen knows to
         * call before travelling rather than assuming a wasted journey is impossible.
         */
        Boolean verified
) {
    /** Pre-HAR-W6 arity, for the internal lane which does not project a verification flag. */
    public FacilityContactDto(String contactType, String name, String phone, String email, String role) {
        this(contactType, name, phone, email, role, null);
    }
}
