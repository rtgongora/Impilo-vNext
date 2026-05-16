# Marketplace Manifest Guide

Every capability published into the Health OS Capability Marketplace
(`msika-apps-service`) is described by a **typed manifest**. The manifest is
the contract between the publisher, the marketplace governance plane and the
hosting Health OS.

## Manifest types

| `CapabilityType`     | Schema                                                                    |
|----------------------|---------------------------------------------------------------------------|
| `APP`                | [`contracts/schemas/app-manifest.schema.json`](../../contracts/schemas/app-manifest.schema.json) |
| `EXTENSION`          | [`extension-manifest.schema.json`](../../contracts/schemas/extension-manifest.schema.json) |
| `PLUGIN`             | [`plugin-manifest.schema.json`](../../contracts/schemas/plugin-manifest.schema.json) |
| `CONNECTOR`          | [`connector-manifest.schema.json`](../../contracts/schemas/connector-manifest.schema.json) |
| `ADAPTER`            | [`adapter-manifest.schema.json`](../../contracts/schemas/adapter-manifest.schema.json) |
| `WORKFLOW_PACK`      | [`workflow-pack-manifest.schema.json`](../../contracts/schemas/workflow-pack-manifest.schema.json) |
| `CONTENT_PACK`       | [`content-pack-manifest.schema.json`](../../contracts/schemas/content-pack-manifest.schema.json) |
| `AI_SKILL`           | [`ai-skill-manifest.schema.json`](../../contracts/schemas/ai-skill-manifest.schema.json) |
| `DEVICE_INTEGRATION` | [`device-integration-manifest.schema.json`](../../contracts/schemas/device-integration-manifest.schema.json) |

Canonical TypeScript types live in
[`contracts/health-os-extensibility.ts`](../../contracts/health-os-extensibility.ts).

## Common required fields

```jsonc
{
  "itemCode": "kebab-case-stable-id",
  "name": "Human readable name",
  "version": "1.0.0",
  "description": "...",
  "publisherId": "pub-...",
  "defaultVisibility": "MOHCC_ONLY",
  "rolesAllowed": ["PROVIDER", "FACILITY_ADMIN"],
  "permittedPurposesOfUse": ["TREATMENT", "OPERATIONS"],
  "supportContact": { "email": "..." },
  // capability-type-specific fields below
}
```

## Visibility tiers

`PUBLIC` · `MOHCC_ONLY` · `FACILITY_ONLY` · `PROGRAMME_ONLY` ·
`PROVINCE_ONLY` · `DISTRICT_ONLY` · `PRIVATE_SECTOR_ONLY` · `SANDBOX_ONLY` ·
`DEVELOPER_PREVIEW` · `DEPRECATED` · `SUSPENDED`.

The visibility tier defines who can **discover** the item. Activation always
requires governance approval regardless of visibility tier.

## Classifications

* `securityClassification`: `LOW` · `STANDARD` · `ELEVATED` · `CRITICAL`
* `dataProtectionClassification`: `NO_PHI` · `PSEUDONYMOUS` · `PHI_LIMITED` · `PHI_FULL`
* `clinicalSafetyClassification`: `NON_CLINICAL` · `CLINICAL_INFORMATIONAL` ·
  `CLINICAL_DECISION_SUPPORT` · `CLINICAL_DIRECT_CARE`

`ELEVATED` or `CRITICAL` security, any `PHI_*` data class, and any
`CLINICAL_DECISION_SUPPORT`/`CLINICAL_DIRECT_CARE` capability all require
explicit clinical safety sign-off before approval.

## Publishing flow

1. Publisher registers (one-time) at `POST /internal/v1/marketplace/publishers`.
2. Publisher submits manifest at `POST /internal/v1/marketplace/items` with
   the JSON manifest as `manifestRef` (artefact URL) and a copy of the
   structured fields.
3. Marketplace governance reviews `approvalStatus` → `IN_REVIEW` → `APPROVED`.
4. Tenants discover via `GET /internal/v1/marketplace/items`.
5. Tenants request activation via
   `POST /internal/v1/marketplace/activation-requests`.
6. Approvers decide via
   `POST /internal/v1/marketplace/activation-requests/{id}/decision`.
7. On approval, an `INSTALLED` installation is auto-created and the tenant
   admin configures + activates it.

## Non-autonomy doctrine for AI skills

AI skills MUST list `prohibitedAutonomousActions`. The Nompilo orchestrator
refuses any tool call that matches a prohibited action description, even if
the underlying tool would technically allow it. Tools with non-`READ_ONLY`
effects MUST set `requiresConfirmation: true`.

See the sample at
[`contracts/ai-skills/nompilo-marketplace-helper.skill.json`](../../contracts/ai-skills/nompilo-marketplace-helper.skill.json).
