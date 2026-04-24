# Tshepo policy — Experience shell workspace (`workspace-state`)

When `impilo.shell-workspace.require-tshepo-authorize=true`, the Experience BFF calls Tshepo synthetic authorize with:

- **`:method`** — `GET`, `PUT`, or `DELETE` (matches the workspace-state HTTP verb).
- **`:path`** — `/internal/v1/shell/workspace-state` (fixed string; see `TshepoAuthzServiceClient.shellWorkspaceStateAllowed`).

## Resource type derivation

`AuthzInternalRequest.deriveResourceType` walks path segments from the **right**, skipping UUIDs and the literals `v1` / `api`. For this path the last segment is **`workspace-state`**, which becomes the **policy `resource_type`** column value.

## Migrations (policy packs)

| Migration | Purpose |
|-----------|---------|
| `V008__shell_workspace_policy_rules.sql` | Baseline `ALLOW` rules for `workspace-state` (SYSTEM_ADMIN, HIE_ADMIN, ADMIN, CLINICIAN, CITIZEN, FINANCE, COMMERCE, DEVELOPER). |
| `V009__shell_workspace_extended_roles.sql` | Additional realm roles used by Experience role groups (NURSE, PHARMACIST, SUPPORT_AGENT, FACILITY_ADMIN, public-health and caregiver roles). |

Apply migrations to the **tshepo-authz-service** database before enabling the PDP gate in production.

## Enabling the gate

1. Run Flyway migrations for `tshepo-authz-service` through **V009** (or merge equivalent rules into your tenant pack).
2. Set `IMPILO_SHELL_WORKSPACE_REQUIRE_TSHEPO=true` on **experience-bff**.
3. Keep `IMPILO_SHELL_WORKSPACE_TSHEPO_FALLBACK_ALLOW=false` only when you want a **hard deny** on Tshepo outage (default `true` logs and allows for resilience).

## Custom tenants

`V008` / `V009` seed the national demo tenant `00000000-0000-0000-0000-000000000001`. For other tenants, copy the rule rows with the appropriate `tenant_id` or manage rules via your Tshepo admin API.
