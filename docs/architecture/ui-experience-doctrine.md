# UI Experience Doctrine

## Principle

Impilo One UI Shell is a journey-aware, transaction-aware, role-aware operating surface where modules remain available but do not dominate the mental model.

## Ordering Rule

1. Journey first.
2. Transaction state second.
3. Role context third.
4. Nompilo assistance always available.

## Operating Rules

- Preserve existing routes and domain work.
- Use `contracts/core-transaction.ts` as canonical contract source.
- Route and component consolidation is additive, not destructive.
- Nompilo never becomes source of truth; sovereign services remain authoritative.
- Shared shell capabilities (search, command palette, context selection, taskbar) should be reused before creating new surfaces.

