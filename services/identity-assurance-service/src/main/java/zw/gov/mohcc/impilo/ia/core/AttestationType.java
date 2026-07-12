package zw.gov.mohcc.impilo.ia.core;

public enum AttestationType {
    DEVICE_BINDING,
    BIOMETRIC,
    OTP,
    SMARTCARD,
    PIN,
    /**
     * A contact channel (phone or email) was verified for the account via an out-of-band
     * one-time code. This is the R1 "Reachable" rung of the gateway trust ladder
     * (docs/doctrine/health-services-gateway-doctrine.md §4): it does NOT raise the
     * assurance level — R1 = LOA1 + this attestation.
     */
    CONTACT_VERIFIED
}
