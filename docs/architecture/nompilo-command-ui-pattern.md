# Nompilo Command UI Pattern

## Objective

Expose one coherent Nompilo command experience across shell and route-level pages without duplicating assistants per module.

## Surfaces

- Global command bar in `AppLayout`.
- Shell command/search palette (`Ctrl+K`, taskbar search button).
- `/ask` for conversational depth.
- `/search` and `/intelligence` for governed retrieval/explanation.

## Behavior

- Typed input, voice dictation, role-aware and journey-aware suggestions.
- Route classification influences suggested commands.
- Escalation path remains `/support` and `/support/tickets`.
- Nompilo guidance is advisory and references source systems.

## Source-of-Truth Boundary

- Nompilo composes and explains.
- Authoritative truth stays in Vito, Varapi, Tuso, Butano, Msika, Costa, MusheX, Data Plane, Document services, and support systems.

