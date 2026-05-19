# Health OS Integration Security Checklist

Hard rules that every plugin, extension, app, connector, adapter, workflow
pack, content pack, AI skill, device integration and external app MUST
satisfy before going live.

## Authentication

- [ ] OAuth2 Client Credentials (system-to-system) or Authorization Code +
      PKCE (user-delegated) is used. Long-lived API keys are permitted only
      in `SANDBOX`.
- [ ] mTLS for any caller dealing with `PHI_FULL` or `CRITICAL` security
      class capabilities.
- [ ] No tokens exposed to frontend plugins. Frontend plugins receive a
      short-lived, scope-restricted delegation issued by the BFF.

## Authorisation

- [ ] All endpoints carry a TSHEPO scope or role check (`@PreAuthorize` in
      Java, route guards in TypeScript).
- [ ] Caller's `purposeOfUse` is checked against the operation policy.
- [ ] Tenant isolation: every read/write filters by `X-Tenant-Id`.
- [ ] Facility / workspace scoping where applicable.
- [ ] Break-glass only for clinically justified scenarios, with full audit.

## Transport

- [ ] TLS 1.2+ everywhere; HSTS on browser surfaces.
- [ ] No sensitive data in query strings.
- [ ] CSP / safe DOM on UI surfaces.
- [ ] Webhook deliveries signed with HMAC-SHA256, replay-protected, skew
      ≤ 300 seconds.

## Data

- [ ] Data minimisation on event payloads; references over full records.
- [ ] No raw secrets / signing material / vault refs in logs or error
      bodies.
- [ ] Sandbox data isolated from production at the network and storage
      layer.
- [ ] Data residency: production data stays in-country per Zimbabwe
      health-data sovereignty doctrine.

## Plugin runtime safety (frontend)

- [ ] Runs inside the approved extension frame / module boundary; no
      direct access to the shell DOM outside its slot.
- [ ] Cannot read auth tokens directly.
- [ ] Cannot make outbound calls to unapproved hosts.
- [ ] Cannot inject scripts into the host.
- [ ] Cannot mutate unrelated shell state.

## Plugin runtime safety (backend)

- [ ] Runs under its own service identity, not as a host service.
- [ ] Only the APIs declared in its manifest are reachable.
- [ ] Sandboxed where feasible (separate container, network policy).
- [ ] Versioned and rollback-able. Tests pass before activation.

## AI skill safety

- [ ] Tools declare effects (`READ_ONLY` / `WRITE_LOW_RISK` /
      `WRITE_HIGH_RISK` / `INVOKE_EXTERNAL`).
- [ ] Any non-`READ_ONLY` tool sets `requiresConfirmation: true`.
- [ ] `prohibitedAutonomousActions` enumerates the high-impact actions
      the skill must never attempt autonomously.
- [ ] Tools route through TSHEPO policy ref.
- [ ] All invocations logged with `actorId`, `actorType=AI_ASSISTED`,
      `purposeOfUse`, `tenantId`.

## Hard prohibitions

* No external app may scrape the UI.
* No external app may directly access internal databases.
* No external app may bypass the gateway, TSHEPO, or audit logging.
* No external app may subscribe to events classified as
  `INTERNAL_PLATFORM`.
* No external app may invoke another external app's webhook subscription
  on its behalf.
* No marketplace item may appear installed but do nothing — every install
  must be backed by real state and a working capability.
