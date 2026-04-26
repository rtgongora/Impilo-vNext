# Registry templates (Impilo vNext)

Machine-readable **schema-driven templates** for the Registry Spine (Vito, Varapi, Tuso) and controlled vocabularies used by the Experience Layer, EHR workflows, and downstream services.

## Layout

| Path | Purpose |
|------|---------|
| `client/` | Vito client / person registration (core, mode, EHR/self/assisted/emergency, coverage). |
| `provider/` | Varapi provider person, professional profile, affiliation, work context. |
| `facility/` | Tuso facility core, location, services, licensing, digital readiness, locality gazetteer. |
| `terminology/` | ISO 3166-1 countries, Zimbabwe admin seeds (see `SOURCE-ZIM-ADMIN.md`), value sets. |
| `manifest.json` | Canonical index of template files for loaders and CI validation. |

## Wiring

- **Experience UI** (`ui/experience`): shared pickers and registration wizards import JSON via `@registry-templates/*` (see `next.config.mjs` + `tsconfig.json`).
- **EHR stub** (`ui/ehr`): uses the same JSON via path alias for country/value-set driven fields.
- **BFF** (`experience-bff`): `PatientController` forwards extended registration metadata to Vito `registerIdentity` when present; local fallback responses are explicitly marked `registryDelegation: false` and `registrySyncState` for offline/provisional flows.

## Zimbabwe administrative data

Province **names** match the ten official provinces. **District and ward seed files are intentionally empty** until an import pipeline loads COD-AB / ZIMSTAT boundaries. **Localities** are Tuso Locality Gazetteer–backed — not static village lists in this repo.

## Standards

Templates carry `fhirConceptualMappings` and `valueSetRef` pointers for alignment with FHIR **Patient**, **Practitioner**, **PractitionerRole**, **Organization**, **Location**, **HealthcareService**, **Coverage**, **Consent**, and **Provenance** / **AuditEvent** where applicable.
