# Vito and the Health ID (HID)

> **Corrected 2026-07-18** — governed by the [Impilo Identity Contract](../architecture/identity-trust-contract.md). The earlier statement that the VITO `healthId` is the same value used as CPID/clinical subject key is **retracted**: CPID is an independent random identifier minted by tshepo-identity, and any flow stamping `health_id` as `patient_cpid` is a contract violation (Identity Contract §7, §17).

Vito's **client / biometric** APIs use `healthId` (`UUID`) — the internal **Health ID (HID)** — as the path parameter and persistence key. That UUID is the same anchor referenced as `impilo_health_id` on Varapi provider profiles and echoed on Tuso/Indawo human relationship rows: those are **actor/identity-plane** usages of the HID (providers, staff, assignees), which is correct and out of scope for CPID rules.

Terminology (Identity Contract §1):

- **Health ID (HID)** — the internal UUID anchor. Trust-core only; never client-facing, never a clinical subject key.
- **Impilo ID** — the distinct, human-friendly client-facing identifier (check-charactered string, issued at proofing approval). This is the only identifier the public is taught.
- **CPID** — the pseudonymous clinical subject identifier used by BUTANO/PCT and all clinical services. Independent of HID; linked only via `tshepo_identity.id_mapping`.

When the Experience Layer resolves "who is this person?" after authentication, it treats the **HID** as the stable anchor server-side, resolves Varapi/MusheX projections as linked identifiers, and obtains the **CPID** from tshepo-identity for any clinical composition. Neither HID nor CPID is emitted to the browser (Identity Contract §12).

See also: `docs/experience/EXPERIENCE_DOCTRINE_IMPILO_IDENTITY.md`.
