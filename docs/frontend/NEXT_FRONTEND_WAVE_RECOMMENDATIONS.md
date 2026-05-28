# Next Frontend Wave Recommendations

After this consolidation sweep, the next wave should **deepen** rather than expand:

1. **UBOMI + BFF** — Ship `/internal/v1/ubomi/*` and replace not-wired page with live workflows.
2. **Core transaction mobile parity** — Provider `CoreTransactionJourneyShell` at parity with web feed + commands.
3. **Registry depth** — VARAPI verification queue, VITO issuance, TUSO control-tower in shell (not satellite-only).
4. **Unified logistics UX** — Merge Nhume + dispatch operator surfaces with shared Ndila map panel.
5. **Experience fork decision** — Deprecate or sync `ui/experience` routes with `one-ui-shell` (258 vs 370).
6. **Automated parity CI** — Run `generate-parity-docs.mjs` + route parity in CI; fail on undeclared launcher dead-ends.
7. **Maturity from registry** — Feed `frontend_wiring_status` from `services-registry.yaml` into badges.

Do **not** add decorative dashboards or duplicate backend logic in the frontend.
