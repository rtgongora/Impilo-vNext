# Vito and Impilo (Health) identity

Vito’s **client / biometric** APIs use `healthId` (`UUID`) as the path parameter and persistence key for biometric profiles and templates. That UUID is the **same Impilo / Health ID** referenced as `impilo_health_id` on Varapi provider profiles and echoed on Tuso/Indawo human relationship rows.

When the Experience Layer resolves “who is this person?” after authentication, it should treat **Health ID** as the stable anchor and only then resolve optional **provider registry** projections (Varapi) or **payer** identities (MusheX) as linked identifiers.

See also: `docs/experience/EXPERIENCE_DOCTRINE_IMPILO_IDENTITY.md`.
