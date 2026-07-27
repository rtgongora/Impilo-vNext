package zw.gov.mohcc.impilo.vito.core;

public enum ClientRelationshipType {
    GUARDIAN_OF,
    DEPENDENT_OF,
    CAREGIVER_OF,
    NEXT_OF_KIN,
    PROXY_ACCESS_FOR,

    // The biological birth dyad, recorded (never inferred) from a live birth. Deliberately distinct
    // from GUARDIAN_OF / CAREGIVER_OF, which are about ACCESS: a birth mother may or may not be the
    // guardian, and a guardian is frequently not the birth mother. BIRTH_MOTHER_OF is the parentage
    // fact itself and confers no access on its own — that stays with the guardianship/consent model.
    // A single mother→child edge is discoverable from BOTH sides via the relationship timeline query,
    // so no inverse child→mother type is minted (an unwritten enum value would be dead vocabulary).
    BIRTH_MOTHER_OF
}
