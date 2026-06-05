# vNext Experience Quality Criteria

> Generated: 2026-06-05T07:37:40.190Z

Assess **journeys**, not isolated pages.

### Intelligent
Nompilo and search explain transaction state with route context. **Assess:** handoff uses `transaction_id`; `/ask` receives pathname context.

### Intuitive
Entry matches actor mental model; steps follow lifecycle stages. **Assess:** goal reachable without unexplained detours.

### Coherent
Surface knows actor, context, intent, transaction, services. **Assess:** orchestration status ≠ isolated/orphan.

### Flowing
Next action visible; correlation preserved across routes. **Assess:** `transaction_id` threads entry → completion.

### Relevant
Live BFF data; role hides irrelevant capability. **Assess:** hook → `/internal/v1/*`; no production fixture fallback.

### Safe
Trust headers, guards, consent, audit on actions. **Assess:** TSHEPO denial + audit event on meaningful mutations.

### Complete
Journey reaches `completionState`; mobile parity where expected. **Assess:** PO acceptance test passable end-to-end.
