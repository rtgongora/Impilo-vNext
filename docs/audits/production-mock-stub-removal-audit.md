# Production mock / stub / fake data — audit

**Principle:** No fake **clinical, financial, consent, or patient** data in production builds. Demos may use **explicitly labelled** PoC seeds (e.g. Zimbabwe Placeholder Tariff v0.1).

## Search terms used (repo)

`mock`, `TODO`, `placeholder`, `sampleData`, `fixture`, `fake`, `coming soon` — **finance/tariffs** had no embedded mock data; the issue was **wrong API** (empty legacy table), not hardcoded UI rows.

## Items

| Location | What | Production risk | Action |
|----------|------|-----------------|--------|
| `ui/experience` vitest | Mocked `useQuery` in tests | None (test only) | Keep |
| COSTA V007 | Zimbabwe **placeholder** seed | **Documented** as PoC | Disclaimers on `/finance/tariffs` + metadata |
| Telemedicine / dictation (various) | Partial stubs | Variable | Tracked in wiring audit P1/P2 |

## Follow-up

- Grep UIs for `const demoPatients` and gate with `NODE_ENV` or `NEXT_PUBLIC_DEMO_MODE`.
- Replace Storybook-style fixtures in shared components with empty/error states for production.
