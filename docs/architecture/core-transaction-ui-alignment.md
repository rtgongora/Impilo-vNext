# Core Transaction UI Alignment

## One UI Shell Alignment

Core transaction scaffolding is introduced under:

- `ui/one-ui-shell/src/features/core-transaction`
- `ui/one-ui-shell/src/app/core-transaction`
- `ui/one-ui-shell/src/app/client-journey`
- `ui/one-ui-shell/src/app/provider-workspace`
- `ui/one-ui-shell/src/app/platform-journey`

## Required UX Signals

1. transaction state badge;
2. transaction type badge;
3. timeline with current/completed/pending/failed/sync/emergency markers;
4. next action panel;
5. trust and identity banners;
6. provider/facility context banner;
7. clinical, financial, follow-up panels;
8. failure mode and offline sync panels.
9. three-journey stage cards and platform journey monitor;
10. Nompilo companion guidance/accessibility/feedback/handoff panels.

## UX Contract

Users must always see:

- where they are in lifecycle,
- what is blocked,
- what is allowed next,
- what has been audited,
- and what still requires reconciliation.
