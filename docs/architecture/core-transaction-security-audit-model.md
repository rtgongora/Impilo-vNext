# Core Transaction Security and Audit Model

## Trust Assertions Per Transaction

Every transaction must capture:

- actor identity and actor type;
- provider role context (where applicable);
- facility/workspace context;
- purpose of use;
- consent/access basis;
- emergency override use;
- correlation identifiers;
- source system and device/session hints.

## Audit Minimum

Every state transition should leave auditable evidence containing:

`who`, `what`, `where`, `why`, `when`, `from_state`, `to_state`, `decision_basis`, `correlation_id`.

## Enforcement

The trust plane (Tshepo/Mvumo) remains the decision plane for authorization and consent; experience and BFF consume those decisions and expose user-safe outcomes.
