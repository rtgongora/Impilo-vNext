package zw.gov.mohcc.impilo.vito.core;

/**
 * SMART Card lifecycle states.
 *
 * State machine transitions:
 *   REQUESTED → PRINTED → ACTIVE → INACTIVE → REVOKED
 *
 * Only one card can be ACTIVE per client at a time (enforced by DB constraint).
 * Card replacement: old card → REVOKED, new card → REQUESTED → ... → ACTIVE.
 */
public enum CardStatus {
    /** Card requested but not yet produced */
    REQUESTED,

    /** Card printed/produced, awaiting activation */
    PRINTED,

    /** Card active and in use */
    ACTIVE,

    /** Card temporarily inactivated */
    INACTIVE,

    /** Card permanently revoked (lost/stolen/damaged/expired/replaced) */
    REVOKED
}
