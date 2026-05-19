# Experience Layer Transaction Anchor

## Purpose

The Experience Plane is the human orchestration surface of the Core Transaction.

## Responsibilities

1. Render transaction state clearly.
2. Render timeline and next allowed actions.
3. Surface trust/consent/permission status.
4. Surface client, provider, facility/workspace context.
5. Surface clinical/financial/follow-up pending work.
6. Surface failure, sync, and emergency reconciliation cues.
7. Surface three synchronized journey views (person/provider/platform).
8. Surface Nompilo guidance, accessibility support, feedback prompts, and handoff options.

## Non-Responsibilities

The Experience Layer must not become a hidden source-of-truth for:

- client identity (Vito);
- provider identity (Varapi);
- facility/workspace identity (Tuso);
- trust/consent decisions (Tshepo/Mvumo);
- clinical facts (Butano/clinical services);
- costing/payment/claims truth (Costa/MusheX).

## BFF Position

Experience BFF composes transaction views from sovereign services and returns a unified UX payload; it does not own underlying domain truth.

## Nompilo Placement

Nompilo is an experience companion layer. It may explain and guide but cannot own truth from Vito, Varapi, Tuso, Tshepo, Butano, Costa, MusheX, or registry/clinical/financial systems.
